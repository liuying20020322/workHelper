CREATE TABLE data_revision (
    id INT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    CONSTRAINT ck_revision_singleton CHECK (id = 1)
);
INSERT INTO data_revision(id, revision) VALUES (1, 0);
