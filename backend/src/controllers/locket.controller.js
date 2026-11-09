const db = require('../config/db');

// Gửi khoảnh khắc Locket (Chụp từ CameraX)
exports.sendMoment = async (req, res) => {
    try {
        const userId = req.user.id;
        const { caption } = req.body;

        if (!req.file) {
            return res.status(400).json({ success: false, message: 'Image file is required.' });
        }

        const imageUrl = `/uploads/${req.file.filename}`;

        const [result] = await db.query(
            'INSERT INTO locket_moments (sender_id, image_url, caption) VALUES (?, ?, ?)',
            [userId, imageUrl, caption || null]
        );

        const [moment] = await db.query(
            `SELECT lm.*, u.full_name as sender_name, u.avatar_url as sender_avatar
             FROM locket_moments lm
             JOIN users u ON u.id = lm.sender_id
             WHERE lm.id = ?`,
            [result.insertId]
        );

        return res.status(201).json({ success: true, message: 'Moment posted successfully.', data: moment[0] });
    } catch (error) {
        console.error('SendMoment error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Lấy khoảnh khắc Locket mới nhất của bạn bè (Dành cho Android Home Screen Widget)
exports.getLatestWidgetMoment = async (req, res) => {
    try {
        const userId = req.user.id;

        // Lấy khoảnh khắc mới nhất từ danh sách bạn bè hoặc từ chính mình nếu chưa có bạn
        const [moments] = await db.query(
            `SELECT lm.id, lm.image_url, lm.caption, lm.created_at,
                    u.full_name as sender_name, u.avatar_url as sender_avatar
             FROM locket_moments lm
             JOIN users u ON u.id = lm.sender_id
             WHERE lm.sender_id IN (
                 SELECT friend_id FROM friendships WHERE user_id = ? AND status = 'ACCEPTED'
             ) OR lm.sender_id = ?
             ORDER BY lm.created_at DESC
             LIMIT 1`,
            [userId, userId]
        );

        if (moments.length === 0) {
            return res.status(200).json({
                success: true,
                data: {
                    imageUrl: '/uploads/default_moment.png',
                    caption: 'Chưa có khoảnh khắc nào. Hãy chụp ảnh đầu tiên!',
                    senderName: 'Locket Chat'
                }
            });
        }

        return res.status(200).json({ success: true, data: moments[0] });
    } catch (error) {
        console.error('GetLatestWidgetMoment error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Lấy danh sách dòng thời gian tất cả khoảnh khắc Locket
exports.getFeedMoments = async (req, res) => {
    try {
        const currentUserId = req.user.id;
        const filterUserId = req.query.userId;

        let query = `
            SELECT lm.id, lm.sender_id, lm.image_url, lm.caption, lm.created_at,
                   u.full_name as sender_name, u.avatar_url as sender_avatar
            FROM locket_moments lm
            JOIN users u ON u.id = lm.sender_id
            WHERE `;
        const params = [];

        if (filterUserId) {
            query += `lm.sender_id = ? `;
            params.push(filterUserId);
        } else {
            query += `(lm.sender_id IN (
                SELECT friend_id FROM friendships WHERE user_id = ? AND status = 'ACCEPTED'
            ) OR lm.sender_id = ?) `;
            params.push(currentUserId, currentUserId);
        }

        query += `ORDER BY lm.created_at DESC LIMIT 50`;

        const [moments] = await db.query(query, params);
        return res.status(200).json({ success: true, data: moments });
    } catch (error) {
        console.error('GetFeedMoments error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};
