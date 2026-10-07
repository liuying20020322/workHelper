CREATE TABLE job_application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    position_name VARCHAR(200) NOT NULL,
    location VARCHAR(200) NOT NULL DEFAULT '',
    requirements TEXT NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    channel VARCHAR(200) NOT NULL DEFAULT '',
    job_url VARCHAR(2000) NOT NULL DEFAULT '',
    notes TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_application_applied_at ON job_application (applied_at, id);
