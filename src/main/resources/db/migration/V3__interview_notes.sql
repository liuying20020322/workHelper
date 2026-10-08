ALTER TABLE application_process ADD COLUMN interview_summary MEDIUMTEXT;
ALTER TABLE application_process ADD COLUMN interview_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE application_process ADD COLUMN question_count INT NOT NULL DEFAULT 0;
CREATE TABLE interview_question (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    process_id BIGINT NOT NULL,
    question MEDIUMTEXT NOT NULL,
    answer MEDIUMTEXT NOT NULL,
    review MEDIUMTEXT NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_question_process FOREIGN KEY (process_id) REFERENCES application_process(id) ON DELETE CASCADE
);
CREATE INDEX idx_question_process ON interview_question(process_id, sort_order, id);
