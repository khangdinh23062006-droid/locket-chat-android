const express = require('express');
const router = express.Router();
const userController = require('../controllers/user.controller');
const verifyToken = require('../middlewares/auth.middleware');
const upload = require('../middlewares/upload.middleware');

router.get('/search', verifyToken, userController.searchByPhone);
router.get('/friends', verifyToken, userController.getFriends);
router.post('/friends', verifyToken, userController.addFriend);
router.put('/profile', verifyToken, upload.single('avatar'), userController.updateProfile);

module.exports = router;
