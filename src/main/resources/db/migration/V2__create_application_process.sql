ALTER TABLE job_application ADD COLUMN current_stage VARCHAR(30) NOT NULL DEFAULT 'APPLIED';
CREATE TABLE application_process (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    stage VARCHAR(30) NOT NULL,
    round_name VARCHAR(200) NOT NULL DEFAULT '',
    time_mode VARCHAR(20) NOT NULL,
    start_at DATETIME(6),
    end_at DATETIME(6),
    deadline_at DATETIME(6),
    status VARCHAR(20) NOT NULL,
    location VARCHAR(2000) NOT NULL DEFAULT '',
    notes TEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_process_application FOREIGN KEY (application_id) REFERENCES job_application(id) ON DELETE CASCADE,
    CONSTRAINT ck_process_stage CHECK (stage IN ('ASSESSMENT','WRITTEN_TEST','INTERVIEW_1','INTERVIEW_2','INTERVIEW_3','OFFER','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_process_status CHECK (status IN ('PENDING','COMPLETED','CANCELLED')),
    CONSTRAINT ck_process_time CHECK (
        (time_mode='RECORD_ONLY' AND start_at IS NULL AND end_at IS NULL AND deadline_at IS NULL)
        OR (time_mode='SCHEDULED' AND start_at IS NOT NULL AND deadline_at IS NULL AND (end_at IS NULL OR end_at>=start_at))
        OR (time_mode='DEADLINE' AND deadline_at IS NOT NULL AND end_at IS NULL AND (start_at IS NULL OR deadline_at>=start_at))
    )
);
CREATE INDEX idx_process_application ON application_process (application_id, occurred_at, id);
