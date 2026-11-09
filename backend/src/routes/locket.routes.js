const express = require('express');
const router = express.Router();
const locketController = require('../controllers/locket.controller');
const verifyToken = require('../middlewares/auth.middleware');
const upload = require('../middlewares/upload.middleware');

router.post('/moment', verifyToken, upload.single('image'), locketController.sendMoment);
router.post('/react', verifyToken, locketController.reactMoment);
router.get('/widget-latest', verifyToken, locketController.getLatestWidgetMoment);
router.get('/feed', verifyToken, locketController.getFeedMoments);

module.exports = router;
