from __future__ import annotations

import asyncio
import hashlib
import uuid

import grpc
from pydantic import ValidationError

from ..ai_service_pb2 import KbDeleteResponse, KbIngestResponse
from ..ai_service_pb2_grpc import KnowledgeBaseServiceServicer
from ..config import Settings
from ..errors import InternalServiceError, bad_request
from ..idempotency import IdempotencyStore
from ..kb import KnowledgeBaseService
from ..models import KbDeleteRequest, KbIngestRequest
from ..utils import stable_json_hash
from ..worker import AsyncWorker
from .base import abort_internal_error, require_internal_auth


class KnowledgeBaseServicer(KnowledgeBaseServiceServicer):
    def __init__(
        self,
        kb_service: KnowledgeBaseService,
        worker: AsyncWorker,
        idempotency_store: IdempotencyStore,
        settings: Settings,
    ) -> None:
        self.kb = kb_service
        self.worker = worker
        self.idempotency_store = idempotency_store
        self.settings = settings

    async def Ingest(self, request, context: grpc.aio.ServicerContext):
        trace_id, metadata_idem = await require_internal_auth(
            context, self.settings, require_trace=True
        )
        class_id = str(request.class_id or "").strip()
        if not class_id:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "classId is required")

        idem_key = (request.idempotency_key or metadata_idem or "").strip()
        if not idem_key:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "Idempotency-Key is required")
        if len(idem_key) < 8 or len(idem_key) > 128:
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                "Idempotency-Key length must be between 8 and 128",
            )

        content_sha = hashlib.sha256(bytes(request.file_content)).hexdigest()
        payload = {
            "traceId": trace_id,
            "jobId": request.job_id,
            "documentId": request.document_id,
            "teacherId": request.teacher_id,
            "classId": class_id,
            "filename": request.filename,
            "fileType": request.file_type,
            "contentSha256": content_sha,
        }
        request_hash = stable_json_hash(payload)
        replay = await self.idempotency_store.get("kb.ingest", idem_key, request_hash)
        if replay is not None:
            return KbIngestResponse(
                status=str(replay.get("status", "ok")),
                job_id=str(replay.get("jobId", "")),
                background=bool(replay.get("background", True)),
                chunks=int(replay.get("chunks", 0)),
            )

        kb_request: KbIngestRequest | None = None
        chunks: list[str] = []
        try:
            kb_request = KbIngestRequest(
                trace_id=trace_id,
                job_id=request.job_id or None,
                document_id=request.document_id,
                teacher_id=request.teacher_id,
                class_id=class_id,
                filename=request.filename,
                file_type=request.file_type or None,
                file_content=bytes(request.file_content),
            )
            chunks = self.kb.extract_and_chunk(kb_request)
        except ValidationError as error:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(error))
        except InternalServiceError as error:
            await abort_internal_error(context, error)
        except Exception as error:
            await abort_internal_error(
                context,
                bad_request(f"knowledge ingest payload invalid: {error}"),
            )

        if kb_request is None:
            await context.abort(grpc.StatusCode.INTERNAL, "knowledge ingest failed")
            return KbIngestResponse(status="error")

        kb_request_ready = kb_request
        chunks_ready = chunks

        job_id = request.job_id or uuid.uuid4().hex
        task = asyncio.create_task(
            self.worker.run_document_embed(
                job_id,
                request.document_id,
                chunks_ready,
                kb_request_ready,
            )
        )
        # prevent GC from collecting the running task (RUF006)
        task.add_done_callback(lambda _t: None)

        result = {
            "status": "ok",
            "jobId": job_id,
            "background": True,
            "chunks": len(chunks_ready),
        }
        await self.idempotency_store.set("kb.ingest", idem_key, request_hash, result)
        return KbIngestResponse(
            status="ok",
            job_id=job_id,
            background=True,
            chunks=len(chunks_ready),
        )

    async def Delete(self, request, context: grpc.aio.ServicerContext):
        trace_id, metadata_idem = await require_internal_auth(
            context, self.settings, require_trace=True
        )
        idem_key = (request.idempotency_key or metadata_idem or "").strip()
        if not idem_key:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "Idempotency-Key is required")
        if len(idem_key) < 8 or len(idem_key) > 128:
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                "Idempotency-Key length must be between 8 and 128",
            )

        payload = {
            "traceId": trace_id,
            "documentId": request.document_id,
        }
        request_hash = stable_json_hash(payload)
        replay = await self.idempotency_store.get("kb.delete", idem_key, request_hash)
        if replay is not None:
            return KbDeleteResponse(status=str(replay.get("status", "ok")))

        try:
            kb_request = KbDeleteRequest(
                trace_id=trace_id,
                document_id=request.document_id,
            )
            self.kb.delete(kb_request)
        except ValidationError as error:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(error))
        except InternalServiceError as error:
            await abort_internal_error(context, error)

        result = {"status": "ok"}
        await self.idempotency_store.set("kb.delete", idem_key, request_hash, result)
        return KbDeleteResponse(status="ok")
