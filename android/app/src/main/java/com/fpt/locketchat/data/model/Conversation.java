package com.fpt.locketchat.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Conversation implements Serializable {
    @SerializedName("id")
    private int id;

    @SerializedName("type")
    private String type; // DIRECT or GROUP

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("display_avatar")
    private String displayAvatar;

    @SerializedName("target_phone")
    private String targetPhone;

    @SerializedName("last_message")
    private String lastMessage;

    @SerializedName("last_message_time")
    private String lastMessageTime;

    public int getId() { return id; }
    public String getType() { return type; }
    public String getDisplayName() { return displayName != null ? displayName : "Phòng chat"; }
    public String getDisplayAvatar() { return displayAvatar; }
    public String getTargetPhone() { return targetPhone; }
    public String getLastMessage() { return lastMessage != null ? lastMessage : ""; }
    public String getLastMessageTime() { return lastMessageTime; }
}
