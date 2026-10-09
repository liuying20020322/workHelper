CREATE TABLE unapplied_company (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL UNIQUE,
    reason VARCHAR(40) NOT NULL,
    other_reason VARCHAR(1000) NOT NULL DEFAULT '',
    viewed_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_unapplied_viewed ON unapplied_company(viewed_date, id);
