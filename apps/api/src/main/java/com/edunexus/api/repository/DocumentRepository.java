package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final String SELECT_COLUMNS =
            """
            select d.id,d.teacher_id,d.classroom_id,c.name as classroom_name,d.filename,d.file_type,d.file_size,
                   d.storage_path,d.status,d.error_message,d.created_at,d.updated_at
            from documents d
            left join classrooms c on c.id=d.classroom_id
            """;

    private static final RowMapper<Document> ROW_MAPPER =
            (rs, rn) ->
                    new Document(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("teacher_id"),
                            (UUID) rs.getObject("classroom_id"),
                            rs.getString("classroom_name"),
                            rs.getString("filename"),
                            rs.getString("file_type"),
                            rs.getLong("file_size"),
                            rs.getString("storage_path"),
                            rs.getString("status"),
                            rs.getString("error_message"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")),
                            ApiDataMapper.toInstant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(
            UUID teacherId,
            UUID classroomId,
            String filename,
            String fileType,
            long fileSize,
            String storagePath) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into documents(id,teacher_id,classroom_id,filename,file_type,file_size,storage_path,status,error_message) values (?,?,?,?,?,?,?,'UPLOADING',null)",
                id,
                teacherId,
                classroomId,
                filename,
                fileType,
                fileSize,
                storagePath);
        return id;
    }

    public Document findById(UUID id) {
        List<Document> rows =
                jdbc.query(
                        SELECT_COLUMNS + " where d.id=? and d.deleted_at is null", ROW_MAPPER, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("文档不存在");
        return rows.getFirst();
    }

    public List<Document> list(UUID teacherId, String status) {
        StringBuilder sql =
                new StringBuilder(
                        SELECT_COLUMNS + " where d.teacher_id=? and d.deleted_at is null");
        List<Object> args = new ArrayList<>();
        args.add(teacherId);
        if (status != null && !status.isBlank()) {
            sql.append(" and d.status=?");
            args.add(status);
        }
        sql.append(" order by d.created_at desc");
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public void updateStatus(UUID id, String status, String errorMessage) {
        jdbc.update(
                "update documents set status=?,error_message=?,updated_at=now() where id=?",
                status,
                errorMessage,
                id);
    }

    public void softDelete(UUID id) {
        jdbc.update("update documents set deleted_at=now(),updated_at=now() where id=?", id);
    }

    public List<Document> findReadyDuplicatesForDocument(UUID documentId) {
        return jdbc.query(
                """
                select dup.id,dup.teacher_id,dup.classroom_id,c.name as classroom_name,dup.filename,dup.file_type,
                       dup.file_size,dup.storage_path,dup.status,dup.error_message,dup.created_at,dup.updated_at
                from documents current_doc
                join documents dup
                  on dup.teacher_id = current_doc.teacher_id
                 and dup.classroom_id = current_doc.classroom_id
                 and lower(dup.filename) = lower(current_doc.filename)
                 and dup.deleted_at is null
                 and dup.status = 'READY'
                 and dup.id <> current_doc.id
                left join classrooms c on c.id = dup.classroom_id
                where current_doc.id = ?
                  and current_doc.deleted_at is null
                order by dup.created_at desc, dup.id desc
                """,
                ROW_MAPPER,
                documentId);
    }

    public List<Document> listSupersededReadyDocuments() {
        return jdbc.query(
                """
                with ranked_documents as (
                  select d.id,
                         row_number() over (
                           partition by d.teacher_id, d.classroom_id, lower(d.filename)
                           order by d.created_at desc, d.id desc
                         ) as row_num
                  from documents d
                  where d.deleted_at is null
                    and d.status = 'READY'
                )
                select d.id,d.teacher_id,d.classroom_id,c.name as classroom_name,d.filename,d.file_type,d.file_size,
                       d.storage_path,d.status,d.error_message,d.created_at,d.updated_at
                from ranked_documents rd
                join documents d on d.id = rd.id
                left join classrooms c on c.id = d.classroom_id
                where rd.row_num > 1
                order by d.teacher_id, d.classroom_id, lower(d.filename), d.created_at desc, d.id desc
                """,
                ROW_MAPPER);
    }

    public UUID findDocumentIdByJobId(UUID jobId) {
        List<UUID> rows =
                jdbc.query(
                        "select business_id from job_runs where id = ? and job_type = 'DOCUMENT_INGEST'",
                        (rs, rn) -> (UUID) rs.getObject("business_id"),
                        jobId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Document> listSoftDeletedDocuments() {
        return jdbc.query(
                """
                select d.id,d.teacher_id,d.classroom_id,c.name as classroom_name,d.filename,d.file_type,d.file_size,
                       d.storage_path,d.status,d.error_message,d.created_at,d.updated_at
                from documents d
                left join classrooms c on c.id = d.classroom_id
                where d.deleted_at is not null
                order by d.created_at desc, d.id desc
                """,
                ROW_MAPPER);
    }

    public Document ensureOwner(UUID documentId, UUID teacherId) {
        Document doc = findById(documentId);
        if (!teacherId.equals(doc.teacherId())) throw new SecurityException("非资源归属者");
        return doc;
    }
}
