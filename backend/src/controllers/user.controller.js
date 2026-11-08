const db = require('../config/db');

// Tìm kiếm người dùng bằng số điện thoại
exports.searchByPhone = async (req, res) => {
    try {
        const { phone } = req.query;
        if (!phone) {
            return res.status(400).json({ success: false, message: 'Please provide phone query.' });
        }

        const [users] = await db.query(
            `SELECT u.id, u.phone_number, u.full_name, u.avatar_url, u.bio,
             COALESCE(f.status, 'NONE') as friendship_status
             FROM users u
             LEFT JOIN friendships f ON (f.user_id = ? AND f.friend_id = u.id)
             WHERE u.phone_number LIKE ? AND u.id != ?
             LIMIT 20`,
            [req.user.id, `%${phone}%`, req.user.id]
        );

        return res.status(200).json({ success: true, data: users });
    } catch (error) {
        console.error('SearchByPhone error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Lấy danh sách bạn bè
exports.getFriends = async (req, res) => {
    try {
        const [friends] = await db.query(
            `SELECT u.id, u.phone_number, u.full_name, u.avatar_url, u.bio, f.created_at as friend_since
             FROM friendships f
             JOIN users u ON u.id = f.friend_id
             WHERE f.user_id = ? AND f.status = 'ACCEPTED'`,
            [req.user.id]
        );

        return res.status(200).json({ success: true, data: friends });
    } catch (error) {
        console.error('GetFriends error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Thêm bạn bè
exports.addFriend = async (req, res) => {
    try {
        const { friendId } = req.body;
        if (!friendId || friendId === req.user.id) {
            return res.status(400).json({ success: false, message: 'Invalid friend ID.' });
        }

        // Tạo quan hệ 2 chiều
        await db.query(
            `INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'ACCEPTED')
             ON DUPLICATE KEY UPDATE status = 'ACCEPTED'`,
            [req.user.id, friendId]
        );
        await db.query(
            `INSERT INTO friendships (user_id, friend_id, status) VALUES (?, ?, 'ACCEPTED')
             ON DUPLICATE KEY UPDATE status = 'ACCEPTED'`,
            [friendId, req.user.id]
        );

        return res.status(200).json({ success: true, message: 'Friend added successfully.' });
    } catch (error) {
        console.error('AddFriend error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Cập nhật thông tin tài khoản
exports.updateProfile = async (req, res) => {
    try {
        const { fullName, bio } = req.body;
        let avatarUrl = req.file ? `/uploads/${req.file.filename}` : undefined;

        let query = 'UPDATE users SET ';
        const params = [];

        if (fullName) {
            query += 'full_name = ?, ';
            params.push(fullName);
        }
        if (bio !== undefined) {
            query += 'bio = ?, ';
            params.push(bio);
        }
        if (avatarUrl) {
            query += 'avatar_url = ?, ';
            params.push(avatarUrl);
        }

        if (params.length === 0) {
            return res.status(400).json({ success: false, message: 'Nothing to update.' });
        }

        query = query.slice(0, -2) + ' WHERE id = ?';
        params.push(req.user.id);

        await db.query(query, params);

        return res.status(200).json({
            success: true,
            message: 'Profile updated successfully.',
            data: { fullName, bio, avatarUrl }
        });
    } catch (error) {
        console.error('UpdateProfile error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};
