-- Create a single database for all services
CREATE DATABASE microservices_db;

-- Connect to the new database to create schemas within it
\c microservices_db;

-- Create schemas for all microservices
CREATE SCHEMA IF NOT EXISTS "order";
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS shipping;
CREATE SCHEMA IF NOT EXISTS "user";
-- Seata will also use its own schema
CREATE SCHEMA IF NOT EXISTS seata; 