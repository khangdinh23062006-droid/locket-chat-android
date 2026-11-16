package com.fpt.locketchat.data.chat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.local.ChatStore;
import com.fpt.locketchat.data.model.Message;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class ChatRepository implements ChatDataSource {
    private final ChatStore store;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final Set<Call<?>> calls = Collections.synchronizedSet(new HashSet<>());
    private Socket socket;
    private Listener listener;
    private int conversationId;
    private volatile boolean closed;
    private boolean loading;
    private int markedId;
    private int markingId;

    public ChatRepository(Context context, int userId) { store = new ChatStore(context, userId); }
    private void post(Runnable action) { main.post(() -> { if (!closed) action.run(); }); }
    @Override public void start(int id, Listener listener) {
        this.conversationId = id;
        this.listener = listener;
        disk.execute(() -> {
            try { List<Message> cached = store.messages(id); post(() -> listener.messages(cached)); }
            catch (RuntimeException e) { post(() -> listener.error("Không đọc được bộ nhớ tin nhắn")); }
        });
        try {
            IO.Options options = new IO.Options();
            options.auth = Collections.singletonMap("token", ApiClient.getAuthToken());
            socket = IO.socket(ApiClient.getBaseHost(), options);
            socket.on(Socket.EVENT_CONNECT, args -> post(() -> {
                listener.connection(true);
                refresh(); // Fetch missed messages after every reconnect.
            }));
            socket.on(Socket.EVENT_DISCONNECT, args -> post(() -> listener.connection(false)));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> post(() -> listener.connection(false)));
            socket.on("new_message", args -> {
                if (args.length == 0) return;
                try {
                    Message message = gson.fromJson(args[0].toString(), Message.class);
                    if (message.getConversationId() == conversationId) post(() -> accept(Collections.singletonList(message)));
                } catch (RuntimeException ignored) {}
            });
            socket.connect();
        } catch (Exception e) { listener.connection(false); }
        refresh();
    }
    private void accept(List<Message> messages) {
        if (closed) return;
        // Schedule writes before delivering data so the disk queue preserves network order.
        disk.execute(() -> {
            try { store.saveMessages(messages); }
            catch (RuntimeException e) { post(() -> listener.error("Không lưu được tin nhắn offline")); }
        });
        post(() -> listener.messages(messages));
    }
    @Override public void refresh() {
        if (closed || loading) return;
        loading = true;
        Call<JsonObject> call = ApiClient.getService().getMessages(conversationId);
        calls.add(call);
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> c, Response<JsonObject> response) {
                calls.remove(c); loading = false;
                if (closed) return;
                if (response.isSuccessful() && response.body() != null && response.body().get("success").getAsBoolean()) {
                    Message[] result = gson.fromJson(response.body().get("data"), Message[].class);
                    accept(Arrays.asList(result));
                } else listener.error("Không tải được lịch sử (" + response.code() + ")");
            }
            @Override public void onFailure(Call<JsonObject> c, Throwable error) {
                calls.remove(c); loading = false;
                if (!closed) { listener.connection(false); listener.error("Đang xem dữ liệu offline. Bấm Thử lại khi có mạng."); }
            }
        });
    }
    private RequestBody text(String value) { return RequestBody.create(MediaType.parse("text/plain"), value); }
    @Override public void send(String content, String type, File file, String mime, String clientId, SendCallback callback) {
        MultipartBody.Part attachment = file == null ? null : MultipartBody.Part.createFormData("file", file.getName(),
                new ProgressRequestBody(file, mime, percent -> post(() -> callback.progress(percent))));
        Call<JsonObject> call = ApiClient.getService().sendMessage(text(String.valueOf(conversationId)),
                text(content), text(type), text(clientId), attachment);
        calls.add(call);
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> c, Response<JsonObject> response) {
                calls.remove(c);
                if (closed) return;
                if (response.isSuccessful() && response.body() != null && response.body().get("success").getAsBoolean()) {
                    Message message = gson.fromJson(response.body().get("data"), Message.class);
                    accept(Collections.singletonList(message));
                    callback.success(message);
                } else callback.failure("Gửi thất bại (" + response.code() + "). Bấm Thử lại.");
            }
            @Override public void onFailure(Call<JsonObject> c, Throwable error) {
                calls.remove(c);
                if (!closed) callback.failure("Không gửi được. Kiểm tra mạng rồi bấm Thử lại.");
            }
        });
    }
    @Override public void markRead(int messageId) {
        if (messageId <= Math.max(markedId, markingId) || closed) return;
        markingId = messageId;
        JsonObject body = new JsonObject(); body.addProperty("messageId", messageId);
        Call<JsonObject> call = ApiClient.getService().markConversationRead(conversationId, body);
        calls.add(call);
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> c, Response<JsonObject> response) {
                calls.remove(c);
                if (response.isSuccessful()) markedId = Math.max(markedId, messageId);
                if (markingId == messageId) markingId = 0;
            }
            @Override public void onFailure(Call<JsonObject> c, Throwable error) {
                calls.remove(c); if (markingId == messageId) markingId = 0;
            }
        });
    }
    @Override public void close() {
        closed = true;
        if (socket != null) { socket.off(); socket.disconnect(); }
        synchronized (calls) { for (Call<?> call : calls) call.cancel(); calls.clear(); }
        disk.execute(store::close);
        disk.shutdown();
        main.removeCallbacksAndMessages(null);
    }
}
