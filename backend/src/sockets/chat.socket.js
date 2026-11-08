module.exports = (io) => {
    io.on('connection', (socket) => {
        console.log(`[Socket] User connected: ${socket.id}`);

        // Tham gia phòng trò chuyện theo conversationId
        socket.on('join_conversation', (conversationId) => {
            socket.join(`conv_${conversationId}`);
            console.log(`[Socket] Socket ${socket.id} joined conv_${conversationId}`);
        });

        // Rời phòng
        socket.on('leave_conversation', (conversationId) => {
            socket.leave(`conv_${conversationId}`);
            console.log(`[Socket] Socket ${socket.id} left conv_${conversationId}`);
        });

        // Gửi tin nhắn mới trong phòng chat
        socket.on('send_message', (messageData) => {
            if (messageData && messageData.conversation_id) {
                // Broadcast cho tất cả thành viên trong room
                io.to(`conv_${messageData.conversation_id}`).emit('new_message', messageData);
            }
        });

        // Đang nhập văn bản (Typing status)
        socket.on('typing', ({ conversationId, userId, userName, isTyping }) => {
            socket.to(`conv_${conversationId}`).emit('user_typing', {
                conversationId,
                userId,
                userName,
                isTyping
            });
        });

        // Bắn sự kiện khoảnh khắc Locket mới (để Widget / App nhận được thông báo)
        socket.on('new_moment', (momentData) => {
            io.emit('moment_broadcast', momentData);
        });

        socket.on('disconnect', () => {
            console.log(`[Socket] User disconnected: ${socket.id}`);
        });
    });
};
