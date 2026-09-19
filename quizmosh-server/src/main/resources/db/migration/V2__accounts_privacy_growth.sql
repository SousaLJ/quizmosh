CREATE TABLE users (id VARCHAR(36) PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE user_identities (
  provider VARCHAR(40) NOT NULL, subject VARCHAR(255) NOT NULL,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY(provider, subject)
);
CREATE INDEX identities_user ON user_identities(user_id);
CREATE TABLE user_profiles (user_id VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, nickname VARCHAR(24) NOT NULL);
CREATE TABLE user_sessions (
  token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX sessions_user ON user_sessions(user_id);
CREATE INDEX sessions_expiry ON user_sessions(expires_at);
CREATE TABLE privacy_preferences (
  subject_id VARCHAR(36) PRIMARY KEY, policy_version VARCHAR(20) NOT NULL,
  analytics BOOLEAN NOT NULL, advertising BOOLEAN NOT NULL, personalization BOOLEAN NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE consents (
  id VARCHAR(36) PRIMARY KEY, subject_id VARCHAR(36) NOT NULL, consent_type VARCHAR(20) NOT NULL,
  granted BOOLEAN NOT NULL, policy_version VARCHAR(20) NOT NULL, recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX consents_subject ON consents(subject_id);
CREATE TABLE referral_links (
  token VARCHAR(36) PRIMARY KEY, subject_id VARCHAR(36) NOT NULL, room_code VARCHAR(8) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE analytics_events (
  id VARCHAR(36) PRIMARY KEY, subject_id VARCHAR(36) NOT NULL, event_type VARCHAR(48) NOT NULL,
  room_code VARCHAR(8), match_id VARCHAR(80), referral VARCHAR(36), occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX analytics_time ON analytics_events(occurred_at);
CREATE INDEX analytics_subject ON analytics_events(subject_id);
CREATE TABLE host_room_creations (
  id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  room_code VARCHAR(8) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX host_rooms_user ON host_room_creations(user_id, created_at);
