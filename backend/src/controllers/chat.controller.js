const db = require('../config/db');

// Lấy danh sách cuộc trò chuyện của user
exports.getConversations = async (req, res) => {
    try {
        const userId = req.user.id;

        const [conversations] = await db.query(
            `SELECT c.id, c.type, c.title, c.avatar_url, c.updated_at,
                    (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.id > cm.last_read_message_id AND m.sender_id != cm.user_id) as unread_count,
                    (SELECT content FROM messages WHERE conversation_id = c.id ORDER BY id DESC LIMIT 1) as last_message,
                    (SELECT message_type FROM messages WHERE conversation_id = c.id ORDER BY id DESC LIMIT 1) as last_message_type,
                    (SELECT created_at FROM messages WHERE conversation_id = c.id ORDER BY id DESC LIMIT 1) as last_message_time,
                    -- Với chat 1-1, lấy tên và avatar của người đối diện
                    (CASE WHEN c.type = 'DIRECT' THEN
                        (SELECT u.full_name FROM conversation_members cm JOIN users u ON u.id = cm.user_id WHERE cm.conversation_id = c.id AND cm.user_id != ? LIMIT 1)
                     ELSE c.title END) as display_name,
                    (CASE WHEN c.type = 'DIRECT' THEN
                        (SELECT u.avatar_url FROM conversation_members cm JOIN users u ON u.id = cm.user_id WHERE cm.conversation_id = c.id AND cm.user_id != ? LIMIT 1)
                     ELSE c.avatar_url END) as display_avatar,
                    (CASE WHEN c.type = 'DIRECT' THEN
                        (SELECT u.phone_number FROM conversation_members cm JOIN users u ON u.id = cm.user_id WHERE cm.conversation_id = c.id AND cm.user_id != ? LIMIT 1)
                     ELSE NULL END) as target_phone
             FROM conversations c
             JOIN conversation_members cm ON cm.conversation_id = c.id
             WHERE cm.user_id = ?
             ORDER BY COALESCE(last_message_time, c.created_at) DESC`,
            [userId, userId, userId, userId]
        );

        return res.status(200).json({ success: true, data: conversations });
    } catch (error) {
        console.error('GetConversations error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Tạo hoặc lấy phòng chat 1-1
exports.getOrCreateDirectChat = async (req, res) => {
    try {
        const userId = req.user.id;
        const { targetUserId } = req.body;

        if (!targetUserId || targetUserId === userId) {
            return res.status(400).json({ success: false, message: 'Invalid target user ID.' });
        }

        // Tìm xem đã có conversation direct giữa 2 người chưa
        const [existing] = await db.query(
            `SELECT c.id FROM conversations c
             JOIN conversation_members cm1 ON cm1.conversation_id = c.id AND cm1.user_id = ?
             JOIN conversation_members cm2 ON cm2.conversation_id = c.id AND cm2.user_id = ?
             WHERE c.type = 'DIRECT' LIMIT 1`,
            [userId, targetUserId]
        );

        if (existing.length > 0) {
            return res.status(200).json({ success: true, data: { conversationId: existing[0].id } });
        }

        // Tạo mới
        const [result] = await db.query("INSERT INTO conversations (type) VALUES ('DIRECT')");
        const convId = result.insertId;

        await db.query(
            'INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, ?), (?, ?, ?)',
            [convId, userId, 'MEMBER', convId, targetUserId, 'MEMBER']
        );

        return res.status(201).json({ success: true, data: { conversationId: convId } });
    } catch (error) {
        console.error('GetOrCreateDirectChat error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Tạo phòng chat nhóm
exports.createGroupChat = async (req, res) => {
    let connection;
    try {
        const userId = req.user.id;
        const { title, memberIds } = req.body;

        if (typeof title !== 'string' || !title.trim() || title.trim().length > 100 ||
            !Array.isArray(memberIds) || memberIds.length === 0 ||
            memberIds.some(id => !Number.isInteger(id) || id <= 0)) {
            return res.status(400).json({ success: false, message: 'Group title and members are required.' });
        }

        const members = [...new Set(memberIds)].filter(id => id !== userId);
        if (members.length < 2 || members.length > 100) {
            return res.status(400).json({ success: false, message: 'Select 2 to 100 friends.' });
        }
        connection = await db.getConnection();
        await connection.beginTransaction();
        const [friends] = await connection.query(
            "SELECT friend_id FROM friendships WHERE user_id = ? AND status = 'ACCEPTED' AND friend_id IN (?)", [userId, members]);
        if (friends.length !== members.length) {
            await connection.rollback();
            return res.status(400).json({ success: false, message: 'All members must be friends.' });
        }

        const [result] = await connection.query(
            "INSERT INTO conversations (type, title) VALUES ('GROUP', ?)",
            [title.trim()]
        );
        const convId = result.insertId;

        // Thêm người tạo làm ADMIN
        await connection.query(
            'INSERT INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, ?)',
            [convId, userId, 'ADMIN']
        );

        // Thêm các thành viên
        for (const mid of members) {
            if (mid !== userId) {
                await connection.query(
                    'INSERT IGNORE INTO conversation_members (conversation_id, user_id, role) VALUES (?, ?, ?)',
                    [convId, mid, 'MEMBER']
                );
            }
        }

        await connection.commit();
        const io = req.app.get('io');
        if (io) for (const id of [userId, ...members]) io.to(`user_${id}`).emit('conversation_changed');

        return res.status(201).json({ success: true, data: { conversationId: convId, title } });
    } catch (error) {
        if (connection) await connection.rollback();
        console.error('CreateGroupChat error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    } finally { if (connection) connection.release(); }
};

// Lấy tin nhắn trong cuộc trò chuyện
exports.getMessages = async (req, res) => {
    try {
        const { conversationId } = req.params;
        const userId = req.user.id;

        // Kiểm tra quyền truy cập phòng chat
        const [check] = await db.query(
            'SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?',
            [conversationId, userId]
        );
        if (check.length === 0) {
            return res.status(403).json({ success: false, message: 'You are not a member of this conversation.' });
        }

        const [messages] = await db.query(
            `SELECT m.id, m.conversation_id, m.sender_id, m.message_type, m.content,
                    m.file_url, m.file_name, m.file_size, m.created_at,
                    u.full_name as sender_name, u.avatar_url as sender_avatar
             FROM messages m
             JOIN users u ON u.id = m.sender_id
             WHERE m.conversation_id = ?
             ORDER BY m.created_at ASC`,
            [conversationId]
        );

        return res.status(200).json({ success: true, data: messages });
    } catch (error) {
        console.error('GetMessages error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    }
};

// Gửi tin nhắn (Text, Image, Video, Audio, File)
exports.sendMessage = async (req, res) => {
    let keepUpload = false;
    try {
        const { conversationId, content, messageType, clientMessageId, fileUrl: bodyFileUrl, fileName: bodyFileName } = req.body;
        const userId = req.user.id;

        const [membership] = await db.query(
            'SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?',
            [conversationId, userId]);
        if (!membership.length) return res.status(403).json({ success: false, message: 'Not a conversation member.' });
        const allowedTypes = ['TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'FILE', 'MOMENT'];
        if (clientMessageId && (typeof clientMessageId !== 'string' || !/^[0-9a-f-]{36}$/i.test(clientMessageId))) {
            return res.status(400).json({ success: false, message: 'Invalid client message ID.' });
        }
        if (!allowedTypes.includes(messageType || 'TEXT') ||
            ((messageType || 'TEXT') === 'TEXT' && (typeof content !== 'string' || !content.trim())) ||
            (['IMAGE', 'VIDEO', 'AUDIO', 'FILE'].includes(messageType) && !req.file)) {
            return res.status(400).json({ success: false, message: 'Invalid message content or attachment.' });
        }

        let type = messageType || 'TEXT';
        let fileUrl = bodyFileUrl || null;
        let fileName = bodyFileName || null;
        let fileSize = null;

        if (req.file) {
            fileUrl = `/uploads/${req.file.filename}`;
            fileName = req.file.originalname;
            fileSize = req.file.size;
        }

        const [result] = await db.query(
            `INSERT INTO messages (conversation_id, sender_id, message_type, content, file_url, file_name, file_size, client_message_id)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)`,
            [conversationId, userId, type, content || null, fileUrl, fileName, fileSize, clientMessageId || null]
        );

        const newMsgId = result.insertId;

        // Lấy thông tin tin nhắn vừa tạo để trả về
        const [savedMsg] = await db.query(
            `SELECT m.*, u.full_name as sender_name, u.avatar_url as sender_avatar
             FROM messages m JOIN users u ON u.id = m.sender_id
             WHERE m.id = ?`,
            [newMsgId]
        );

        keepUpload = !!req.file && savedMsg[0].file_url === `/uploads/${req.file.filename}`;

        if (Number(savedMsg[0].conversation_id) !== Number(conversationId)) {
            return res.status(409).json({ success: false, message: 'Client message ID belongs to another conversation.' });
        }

        // Cập nhật updated_at cho conversation
        await db.query('UPDATE conversations SET updated_at = NOW() WHERE id = ?', [conversationId]);

        // Emit from the server after persistence. Clients must not relay arbitrary messages.
        const [members] = await db.query('SELECT user_id FROM conversation_members WHERE conversation_id = ?', [conversationId]);
        const io = req.app.get('io');
        if (io) for (const member of members) {
            io.to(`user_${member.user_id}`).emit('new_message', savedMsg[0]);
            io.to(`user_${member.user_id}`).emit('conversation_changed');
        }

        return res.status(201).json({ success: true, data: savedMsg[0] });
    } catch (error) {
        console.error('SendMessage error:', error);
        return res.status(500).json({ success: false, message: 'Internal server error.' });
    } finally {
        // Failed requests and idempotent retries must not leave unused attachments.
        if (req.file && !keepUpload) require('node:fs').unlink(req.file.path, () => {});
    }
};

exports.markRead = async (req, res) => {
    try {
        const conversationId = Number(req.params.conversationId);
        const messageId = Number(req.body.messageId);
        if (!Number.isInteger(messageId) || messageId <= 0) return res.status(400).json({ success: false });
        const [result] = await db.query(
            'UPDATE conversation_members SET last_read_message_id = GREATEST(last_read_message_id, ?) WHERE conversation_id = ? AND user_id = ? AND EXISTS (SELECT 1 FROM messages WHERE id = ? AND conversation_id = ?)',
            [messageId, conversationId, req.user.id, messageId, conversationId]);
        if (!result.affectedRows) return res.status(403).json({ success: false });
        const io = req.app.get('io');
        if (io) io.to(`user_${req.user.id}`).emit('conversation_changed');
        res.json({ success: true });
    } catch (error) {
        console.error('MarkRead error:', error);
        res.status(500).json({ success: false });
    }
};
