-- ===================================================
-- LOCKET CHAT DATABASE SCHEMA (MySQL)
-- Subject: Lap trinh di dong Android
-- ===================================================

CREATE DATABASE IF NOT EXISTS locket_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE locket_chat_db;

-- 1. Bảng người dùng (Users)
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(15) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(255) DEFAULT 'default_avatar.png',
    bio VARCHAR(255) DEFAULT 'Hey there! I am using Locket Chat.',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone_number)
) ENGINE=InnoDB;

-- 2. Bảng quan hệ bạn bè (Friendships)
CREATE TABLE IF NOT EXISTS friendships (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'BLOCKED') DEFAULT 'ACCEPTED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_friendship (user_id, friend_id),
    CONSTRAINT fk_friend_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friend_target FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Bảng phòng trò chuyện (Conversations)
CREATE TABLE IF NOT EXISTS conversations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type ENUM('DIRECT', 'GROUP') NOT NULL DEFAULT 'DIRECT',
    title VARCHAR(100) NULL,
    avatar_url VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 4. Bảng thành viên phòng trò chuyện (Conversation Members)
CREATE TABLE IF NOT EXISTS conversation_members (
    conversation_id INT NOT NULL,
    user_id INT NOT NULL,
    role ENUM('MEMBER', 'ADMIN') DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_cm_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Bảng tin nhắn (Messages)
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    conversation_id INT NOT NULL,
    sender_id INT NOT NULL,
    message_type ENUM('TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'FILE', 'MOMENT') NOT NULL DEFAULT 'TEXT',
    content TEXT NULL,
    file_url VARCHAR(255) NULL,
    file_name VARCHAR(255) NULL,
    file_size INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_time (conversation_id, created_at),
    CONSTRAINT fk_msg_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. Bảng khoảnh khắc Locket (Locket Moments - Widget & Camera)
CREATE TABLE IF NOT EXISTS locket_moments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    caption VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_moment_sender (sender_id, created_at),
    CONSTRAINT fk_moment_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 7. Bảng biểu cảm cảm xúc khoảnh khắc (Moment Reactions / Hoạt động)
CREATE TABLE IF NOT EXISTS moment_reactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    moment_id INT NOT NULL,
    user_id INT NOT NULL,
    emoji VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_moment_user (moment_id, user_id),
    CONSTRAINT fk_mr_moment FOREIGN KEY (moment_id) REFERENCES locket_moments(id) ON DELETE CASCADE,
    CONSTRAINT fk_mr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ===================================================
-- SEED DATA (Dữ liệu mẫu để kiểm thử đồ án)
-- Mật khẩu mẫu: 123456 (Bcrypt hash: $2a$10$wE8wU/uFk1E5c5m7nBf.e.Hh5gQJbY0kUqJp8V1qVbJjWjL/uR0K2)
-- ===================================================
INSERT INTO users (id, phone_number, password_hash, full_name, avatar_url, bio) VALUES
(1, '0901234567', '$2a$10$XV7v4g3sHIhFCmw79U4jt.lhk8PfCOcbSYjDP.9vjI2FYD.U9ZH.G', 'Nguyen Van A', 'default_avatar.png', 'Locket Lover 📸'),
(2, '0912345678', '$2a$10$wE8wU/uFk1E5c5m7nBf.e.Hh5gQJbY0kUqJp8V1qVbJjWjL/uR0K2', 'Tran Thi B', 'default_avatar.png', 'Coffee & Code ☕'),
(3, '0987654321', '$2a$10$wE8wU/uFk1E5c5m7nBf.e.Hh5gQJbY0kUqJp8V1qVbJjWjL/uR0K2', 'Le Van C', 'default_avatar.png', 'Android Dev 📱'),
(4, '0978901234', '$2a$10$wE8wU/uFk1E5c5m7nBf.e.Hh5gQJbY0kUqJp8V1qVbJjWjL/uR0K2', 'Pham Thi D', 'default_avatar.png', 'Music on, world off 🎧')
ON DUPLICATE KEY UPDATE id=id;

-- Kết bạn giữa user 1 và user 2, 3
INSERT INTO friendships (user_id, friend_id, status) VALUES
(1, 2, 'ACCEPTED'),
(2, 1, 'ACCEPTED'),
(1, 3, 'ACCEPTED'),
(3, 1, 'ACCEPTED')
ON DUPLICATE KEY UPDATE id=id;

-- Tạo 1 cuộc trò chuyện trực tiếp 1-1 giữa user 1 và user 2
INSERT INTO conversations (id, type, title) VALUES (1, 'DIRECT', NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO conversation_members (conversation_id, user_id, role) VALUES
(1, 1, 'MEMBER'),
(1, 2, 'MEMBER')
ON DUPLICATE KEY UPDATE conversation_id=conversation_id;

-- Tin nhắn mẫu
INSERT INTO messages (conversation_id, sender_id, message_type, content) VALUES
(1, 1, 'TEXT', 'Chào bạn! Ứng dụng Locket Chat chạy mượt quá!'),
(1, 2, 'TEXT', 'Chào bạn A! Mình vừa nhận được ảnh widget của bạn nè!')
ON DUPLICATE KEY UPDATE id=id;
