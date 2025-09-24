-- Database initialization script
USE dbreader_db;

-- Create the data_records table
CREATE TABLE IF NOT EXISTS data_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    processed_by VARCHAR(255) NULL,
    INDEX idx_processed_at (processed_at)
);

-- Insert some test data
INSERT INTO data_records (message, created_at) VALUES 
('Test message 1', NOW()),
('Test message 2', NOW()),
('Test message 3', NOW()),
('Test message 4', NOW()),
('Test message 5', NOW());

-- Insert a test record every minute (for testing purposes)
-- Note: This is just for demonstration, you can add more records manually