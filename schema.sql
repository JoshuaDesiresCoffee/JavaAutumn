-- Database Schema for Room Management System
-- SQLite Database

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL
);

-- Rooms Table
CREATE TABLE IF NOT EXISTS rooms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    capacity INTEGER NOT NULL
);

-- Buildings Table
CREATE TABLE IF NOT EXISTS buildings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    location TEXT NOT NULL
);

-- Bookings Table (references rooms and users)
CREATE TABLE IF NOT EXISTS bookings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    room_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Insert Sample Data (optional)

-- Sample Users
INSERT INTO users (name, email) VALUES ('John Doe', 'john@example.com');
INSERT INTO users (name, email) VALUES ('Jane Smith', 'jane@example.com');
INSERT INTO users (name, email) VALUES ('Bob Wilson', 'bob@example.com');

-- Sample Rooms
INSERT INTO rooms (name, type, capacity) VALUES ('Conference Room A', 'Conference', 20);
INSERT INTO rooms (name, type, capacity) VALUES ('Meeting Room B', 'Meeting', 10);
INSERT INTO rooms (name, type, capacity) VALUES ('Training Room C', 'Training', 30);

-- Sample Buildings
INSERT INTO buildings (name, location) VALUES ('Main Building', 'Downtown');
INSERT INTO buildings (name, location) VALUES ('Branch Office', 'Uptown');

-- Sample Bookings
INSERT INTO bookings (room_id, user_id) VALUES (1, 1);
INSERT INTO bookings (room_id, user_id) VALUES (2, 2);

