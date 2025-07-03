-- Create database for saga orchestrator
CREATE DATABASE saga_db;

-- Connect to saga_db
\c saga_db;

-- Create saga_instance table
CREATE TABLE IF NOT EXISTS saga_instance (
    id VARCHAR(255) PRIMARY KEY,
    version BIGINT NOT NULL,
    saga_type VARCHAR(100) NOT NULL,
    current_state VARCHAR(100) NOT NULL,
    saga_data TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    timeout_at TIMESTAMP,
    last_retry_count INTEGER DEFAULT 0,
    next_retry_time TIMESTAMP,
    compensation_triggered BOOLEAN DEFAULT FALSE,
    compensation_state VARCHAR(100)
);

-- Create saga_checkpoints table
CREATE TABLE IF NOT EXISTS saga_checkpoints (
    saga_id VARCHAR(255) NOT NULL,
    checkpoint_order INTEGER NOT NULL,
    state VARCHAR(100) NOT NULL,
    event VARCHAR(100),
    action_result TEXT,
    timestamp TIMESTAMP NOT NULL,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    PRIMARY KEY (saga_id, checkpoint_order),
    FOREIGN KEY (saga_id) REFERENCES saga_instance(id) ON DELETE CASCADE
);

-- Create saga_history table
CREATE TABLE IF NOT EXISTS saga_history (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(100) NOT NULL,
    source_state VARCHAR(100) NOT NULL,
    target_state VARCHAR(100) NOT NULL,
    event VARCHAR(100) NOT NULL,
    action_name VARCHAR(255),
    action_result TEXT,
    error_message TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL,
    instance_id VARCHAR(100) NOT NULL
);

-- Create indexes for better performance
CREATE INDEX idx_saga_instance_saga_type ON saga_instance(saga_type);
CREATE INDEX idx_saga_instance_status ON saga_instance(status);
CREATE INDEX idx_saga_instance_current_state ON saga_instance(current_state);
CREATE INDEX idx_saga_instance_timeout ON saga_instance(timeout_at);
CREATE INDEX idx_saga_instance_retry ON saga_instance(next_retry_time);
CREATE INDEX idx_saga_instance_compensation ON saga_instance(compensation_triggered, compensation_state);

CREATE INDEX idx_saga_history_saga_id ON saga_history(saga_id);
CREATE INDEX idx_saga_history_saga_type ON saga_history(saga_type);
CREATE INDEX idx_saga_history_states ON saga_history(source_state, target_state);
CREATE INDEX idx_saga_history_created_at ON saga_history(created_at);

CREATE INDEX idx_saga_checkpoints_saga_id ON saga_checkpoints(saga_id);
CREATE INDEX idx_saga_checkpoints_state ON saga_checkpoints(state);
CREATE INDEX idx_saga_checkpoints_timestamp ON saga_checkpoints(timestamp);

-- Create state_machine_context table for Spring State Machine persistence
CREATE TABLE IF NOT EXISTS state_machine_context (
    machine_id VARCHAR(255) PRIMARY KEY,
    state VARCHAR(100),
    state_machine_context BYTEA,
    version BIGINT NOT NULL DEFAULT 0
);

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE saga_db TO "user";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "user";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "user"; 