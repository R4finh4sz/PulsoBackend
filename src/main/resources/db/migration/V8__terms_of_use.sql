CREATE TABLE terms_of_use (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    version BIGINT NOT NULL,
    title VARCHAR(200),
    content TEXT
);
INSERT INTO terms_of_use (id, version) VALUES (1, 0);
