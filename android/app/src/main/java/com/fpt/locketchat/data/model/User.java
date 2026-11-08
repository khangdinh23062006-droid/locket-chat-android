package com.fpt.locketchat.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class User implements Serializable {
    @SerializedName("id")
    private int id;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("bio")
    private String bio;

    public User(int id, String phoneNumber, String fullName, String avatarUrl, String bio) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
    }

    public int getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getFullName() { return fullName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getBio() { return bio; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setBio(String bio) { this.bio = bio; }
}
