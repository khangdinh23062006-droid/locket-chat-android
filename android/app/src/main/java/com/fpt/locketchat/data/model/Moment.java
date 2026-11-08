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

    public int getId() { return id; }
    public int getSenderId() { return senderId; }
    public String getSenderName() { return senderName != null ? senderName : "Locket Friend"; }
    public String getImageUrl() { return imageUrl; }
    public String getCaption() { return caption != null ? caption : ""; }
    public String getCreatedAt() { return createdAt; }
}
