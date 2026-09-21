-- Shared canonical data; translations never change answers, categories or regions.
CREATE TABLE quiz_categories (
    id VARCHAR(80) PRIMARY KEY,
    sort_order INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE quiz_category_texts (
    category_id VARCHAR(80) NOT NULL REFERENCES quiz_categories(id),
    language VARCHAR(12) NOT NULL,
    name VARCHAR(120) NOT NULL,
    PRIMARY KEY (category_id, language)
);
CREATE TABLE quiz_questions (
    id VARCHAR(80) PRIMARY KEY,
    category_id VARCHAR(80) NOT NULL REFERENCES quiz_categories(id),
    question_type VARCHAR(12) NOT NULL CHECK (question_type IN ('choice', 'guess', 'numeric')),
    correct_index INTEGER,
    numeric_value NUMERIC(24,6),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK ((question_type = 'choice' AND correct_index BETWEEN 0 AND 3 AND correct_index IS NOT NULL AND numeric_value IS NULL)
        OR (question_type = 'numeric' AND numeric_value IS NOT NULL AND correct_index IS NULL)
        OR (question_type = 'guess' AND numeric_value IS NULL AND correct_index IS NULL))
);
CREATE INDEX quiz_questions_category ON quiz_questions(category_id, enabled);
CREATE TABLE quiz_question_regions (
    question_id VARCHAR(80) NOT NULL REFERENCES quiz_questions(id),
    region VARCHAR(8) NOT NULL,
    PRIMARY KEY (question_id, region)
);
CREATE TABLE quiz_question_texts (
    question_id VARCHAR(80) NOT NULL REFERENCES quiz_questions(id),
    language VARCHAR(12) NOT NULL,
    prompt TEXT NOT NULL,
    explanation TEXT NOT NULL,
    options_json TEXT,
    clues_json TEXT,
    answers_json TEXT,
    unit VARCHAR(80),
    PRIMARY KEY (question_id, language)
);
