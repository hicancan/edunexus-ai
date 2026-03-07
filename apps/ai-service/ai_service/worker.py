import logging
from typing import Any

import grpc
from google.protobuf.timestamp_pb2 import Timestamp

from . import worker_tasks_pb2, worker_tasks_pb2_grpc
from .config import Settings

logger = logging.getLogger("edunexus.ai.worker")


class AsyncWorker:
    def __init__(self, settings: Settings, kb_service):
        self.settings = settings
        self.kb_service = kb_service
        self.channel = grpc.aio.insecure_channel(settings.java_grpc_url)
        self.status_stub = worker_tasks_pb2_grpc.JobStatusServiceStub(self.channel)

    async def report_status(
        self,
        job_id: str,
        status: worker_tasks_pb2.JobStatus,
        error_message: str = "",
        *,
        trace_id: str = "",
        chunks: int = 0,
    ):
        try:
            now = Timestamp()
            now.GetCurrentTime()

            req = worker_tasks_pb2.JobStatusReportRequest(
                job_id=job_id,
                status=status,
                error_message=error_message,
                reported_at=now,
                trace_id=trace_id,
                chunks=chunks,
            )
            resp = await self.status_stub.ReportStatus(req, timeout=10.0)
            if not resp.success:
                logger.error(
                    "Failed to report job status %s for %s: Java backend rejected via RPC",
                    status,
                    job_id,
                )
        except grpc.RpcError as e:
            logger.error(
                "Failed to report job status %s for %s: RPC error %s",
                status,
                job_id,
                e.code(),
            )
        except Exception:
            logger.exception("Unexpected error reporting job status %s for %s", status, job_id)

    async def run_document_embed(
        self, job_id: str, document_id: str, chunk_texts: list[str], req: Any
    ):
        """
        Runs the embedding asynchronously and reports status.
        """
        trace_id = str(getattr(req, "trace_id", "") or "")
        logger.info("Worker starting JOB_DOCUMENT_EMBED %s for doc %s", job_id, document_id)
        await self.report_status(
            job_id,
            worker_tasks_pb2.JOB_STATUS_RUNNING,
            trace_id=trace_id,
        )

        try:
            # We assume chunk_texts has already been prepared before this heavy task
            # Call the kb_service method that does the actual embedding and Qdrant push
            chunk_count = await self.kb_service.embed_and_upsert_chunks(
                document_id,
                chunk_texts,
                req,
            )

            logger.info("Worker finished JOB_DOCUMENT_EMBED %s successfully", job_id)
            await self.report_status(
                job_id,
                worker_tasks_pb2.JOB_STATUS_SUCCEEDED,
                trace_id=trace_id,
                chunks=chunk_count,
            )

        except Exception as error:
            logger.exception("Worker failed JOB_DOCUMENT_EMBED %s", job_id)
            await self.report_status(
                job_id,
                worker_tasks_pb2.JOB_STATUS_FAILED,
                str(error),
                trace_id=trace_id,
            )
