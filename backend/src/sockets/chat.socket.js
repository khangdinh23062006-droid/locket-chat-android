const jwt = require('jsonwebtoken');
module.exports = (io) => {
    io.use((socket, next) => {
        try {
            const user = jwt.verify(socket.handshake.auth.token, process.env.JWT_SECRET || 'locket_chat_super_secret_jwt_key_2026');
            if (!Number.isInteger(user.id)) return next(new Error('Invalid account'));
            socket.userId = user.id;
            next();
        } catch (error) { next(new Error('Authentication required')); }
    });
    io.on('connection', socket => socket.join(`user_${socket.userId}`));
    // Only the REST controller broadcasts messages after persistence.
};
