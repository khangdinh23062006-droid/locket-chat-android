package com.fpt.locketchat.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Moment implements Serializable {
    @SerializedName("id")
    private int id;

    @SerializedName("sender_id")
    private int senderId;

    @SerializedName("sender_name")
    private String senderName;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("caption")
    private String caption;

    @SerializedName("created_at")
    private String createdAt;

    public static class Reaction implements Serializable {
        @SerializedName("moment_id")
        private int momentId;

        @SerializedName("user_id")
        private int userId;

        @SerializedName("emoji")
        private String emoji;

        @SerializedName("user_name")
        private String userName;

        @SerializedName("avatar_url")
        private String avatarUrl;

        public int getMomentId() { return momentId; }
        public int getUserId() { return userId; }
        public String getEmoji() { return emoji != null ? emoji : "❤️"; }
        public String getUserName() { return userName != null ? userName : ""; }
        public String getAvatarUrl() { return avatarUrl; }
    }

    @SerializedName("reactions")
    private java.util.List<Reaction> reactions;

    public int getId() { return id; }
    public int getSenderId() { return senderId; }
    public String getSenderName() { return senderName != null ? senderName : "Locket Friend"; }
    public String getImageUrl() { return imageUrl; }
    public String getCaption() { return caption != null ? caption : ""; }
    public String getCreatedAt() { return createdAt; }
    public java.util.List<Reaction> getReactions() {
        return reactions != null ? reactions : new java.util.ArrayList<>();
    }
}
