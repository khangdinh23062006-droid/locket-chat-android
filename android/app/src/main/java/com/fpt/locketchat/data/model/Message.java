package com.fpt.locketchat.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Message implements Serializable {
    @SerializedName("id")
    private int id;

    @SerializedName("conversation_id")
    private int conversationId;

    @SerializedName("sender_id")
    private int senderId;

    @SerializedName("sender_name")
    private String senderName;

    @SerializedName("sender_avatar")
    private String senderAvatar;

    @SerializedName("message_type")
    private String messageType; // TEXT, IMAGE, VIDEO, AUDIO, FILE, MOMENT

    @SerializedName("content")
    private String content;

    @SerializedName("file_url")
    private String fileUrl;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("file_size")
    private Integer fileSize;

    @SerializedName("created_at")
    private String createdAt;

    public int getId() { return id; }
    public int getConversationId() { return conversationId; }
    public int getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderAvatar() { return senderAvatar; }
    public String getMessageType() { return messageType; }
    public String getContent() { return content; }
    public String getFileUrl() { return fileUrl; }
    public String getFileName() { return fileName; }
    public Integer getFileSize() { return fileSize; }
    public String getCreatedAt() { return createdAt; }
}
