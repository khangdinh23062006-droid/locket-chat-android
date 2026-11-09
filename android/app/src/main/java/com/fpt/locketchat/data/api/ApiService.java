package com.fpt.locketchat.data.api;

import com.fpt.locketchat.data.model.Conversation;
import com.fpt.locketchat.data.model.Message;
import com.fpt.locketchat.data.model.Moment;
import com.fpt.locketchat.data.model.User;
import com.google.gson.JsonObject;
import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // Auth
    @POST("auth/login")
    Call<JsonObject> login(@Body JsonObject body);

    @POST("auth/register")
    Call<JsonObject> register(@Body JsonObject body);

    @GET("auth/me")
    Call<JsonObject> getMe();

    // User & Friends
    @GET("users/search")
    Call<JsonObject> searchByPhone(@Query("phone") String phone);

    @GET("users/friends")
    Call<JsonObject> getFriends();

    @POST("users/friends")
    Call<JsonObject> addFriend(@Body JsonObject body);

    @PUT("users/profile")
    Call<JsonObject> updateProfile(@Body JsonObject body);

    @Multipart
    @PUT("users/profile")
    Call<JsonObject> updateProfile(
            @Part MultipartBody.Part avatar,
            @Part("bio") RequestBody bio
    );

    // Chat
    @GET("chat/conversations")
    Call<JsonObject> getConversations();

    @POST("chat/direct")
    Call<JsonObject> getOrCreateDirectChat(@Body JsonObject body);

    @POST("chat/group")
    Call<JsonObject> createGroupChat(@Body JsonObject body);

    @GET("chat/conversations/{id}/messages")
    Call<JsonObject> getMessages(@Path("id") int conversationId);

    @Multipart
    @POST("chat/messages")
    Call<JsonObject> sendMessage(
            @Part("conversationId") RequestBody conversationId,
            @Part("content") RequestBody content,
            @Part("messageType") RequestBody messageType,
            @Part MultipartBody.Part file
    );

    // Locket Moments & Widget
    @Multipart
    @POST("locket/moment")
    Call<JsonObject> postMoment(
            @Part MultipartBody.Part image,
            @Part("caption") RequestBody caption
    );

    @GET("locket/widget-latest")
    Call<JsonObject> getLatestWidgetMoment();

    @GET("locket/feed")
    Call<JsonObject> getFeedMoments();

    @GET("locket/feed")
    Call<JsonObject> getFeedMoments(@Query("userId") Integer userId);
}
