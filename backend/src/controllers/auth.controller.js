const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('../config/db');

exports.register = async (req, res) => {
    try {
        const { phoneNumber, password, fullName } = req.body;

        if (!phoneNumber || !password || !fullName) {
            return res.status(400).json({ success: false, message: 'Please provide phone number, password, and full name.' });
        }

        // Check if phone number already exists
        const [existing] = await db.query('SELECT id FROM users WHERE phone_number = ?', [phoneNumber]);
        if (existing.length > 0) {
            return res.status(409).json({ success: false, message: 'Phone number is already registered.' });
        }

        // Hash password
        const salt = await bcrypt.genSalt(10);
        const passwordHash = await bcrypt.hash(password, salt);

        // Insert new user
        const [result] = await db.query(
            'INSERT INTO users (phone_number, password_hash, full_name) VALUES (?, ?, ?)',
            [phoneNumber, passwordHash, fullName]
        );

        const userId = result.insertId;
        const token = jwt.sign(
            { id: userId, phoneNumber },
            process.env.JWT_SECRET || 'locket_chat_super_secret_jwt_key_2026',
            { expiresIn: '30d' }
        );

        return res.status(201).json({
            success: true,
            message: 'User registered successfully.',
            data: {
                token,
                user: { id: userId, phoneNumber, fullName, avatarUrl: 'default_avatar.png' }
            }
        });
    } catch (error) {
        console.error('Register error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

exports.login = async (req, res) => {
    try {
        const { phoneNumber, password } = req.body;

        if (!phoneNumber || !password) {
            return res.status(400).json({ success: false, message: 'Please provide phone number and password.' });
        }

        const [users] = await db.query('SELECT * FROM users WHERE phone_number = ?', [phoneNumber]);
        if (users.length === 0) {
            return res.status(401).json({ success: false, message: 'Invalid phone number or password.' });
        }

        const user = users[0];
        const isMatch = await bcrypt.compare(password, user.password_hash);
        if (!isMatch) {
            return res.status(401).json({ success: false, message: 'Invalid phone number or password.' });
        }

        const token = jwt.sign(
            { id: user.id, phoneNumber: user.phone_number },
            process.env.JWT_SECRET || 'locket_chat_super_secret_jwt_key_2026',
            { expiresIn: '30d' }
        );

        return res.status(200).json({
            success: true,
            message: 'Login successful.',
            data: {
                token,
                user: {
                    id: user.id,
                    phoneNumber: user.phone_number,
                    fullName: user.full_name,
                    avatarUrl: user.avatar_url,
                    bio: user.bio
                }
            }
        });
    } catch (error) {
        console.error('Login error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

exports.getMe = async (req, res) => {
    try {
        const [users] = await db.query(
            'SELECT id, phone_number, full_name, avatar_url, bio, created_at FROM users WHERE id = ?',
            [req.user.id]
        );

        if (users.length === 0) {
            return res.status(404).json({ success: false, message: 'User not found.' });
        }

        return res.status(200).json({ success: true, data: users[0] });
    } catch (error) {
        console.error('GetMe error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};
