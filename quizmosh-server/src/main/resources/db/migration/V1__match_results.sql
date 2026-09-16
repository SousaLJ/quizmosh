CREATE TABLE match_results (
  id VARCHAR(80) PRIMARY KEY,
  room_code VARCHAR(8) NOT NULL,
  finished_at TIMESTAMP WITH TIME ZONE NOT NULL,
  payload TEXT NOT NULL
);
CREATE INDEX match_results_finished_at ON match_results(finished_at);
