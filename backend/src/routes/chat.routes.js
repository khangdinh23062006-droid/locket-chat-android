const express = require('express');
const router = express.Router();
const chatController = require('../controllers/chat.controller');
const verifyToken = require('../middlewares/auth.middleware');
const upload = require('../middlewares/upload.middleware');

router.get('/conversations', verifyToken, chatController.getConversations);
router.post('/direct', verifyToken, chatController.getOrCreateDirectChat);
router.post('/group', verifyToken, chatController.createGroupChat);
router.get('/conversations/:conversationId/messages', verifyToken, chatController.getMessages);
router.post('/conversations/:conversationId/read', verifyToken, chatController.markRead);
router.post('/messages', verifyToken, upload.single('file'), chatController.sendMessage);

module.exports = router;
