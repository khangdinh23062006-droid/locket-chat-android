package com.fpt.locketchat.ui.chat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Message;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private int conversationId;
    private int currentUserId;
    private String displayName;

    private TextView tvChatTitle;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private MessageAdapter adapter;
    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getIntExtra("conversation_id", 0);
        displayName = getIntent().getStringExtra("display_name");

        SharedPreferences prefs = getSharedPreferences("locket_prefs", MODE_PRIVATE);
        currentUserId = prefs.getInt("user_id", 0);

        initViews();
        setupRecyclerView();
        initSocket();
        loadMessageHistory();
        setupListeners();
    }

    private void initViews() {
        tvChatTitle = findViewById(R.id.tvChatTitle);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        if (displayName != null) {
            tvChatTitle.setText(displayName);
        }
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(this, currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
    }

    private void initSocket() {
        try {
            // Địa chỉ server socket.io (Emulator dùng 10.0.2.2:5000)
            socket = IO.socket("http://10.0.2.2:5000");
            socket.connect();

            // Tham gia room phòng chat
            socket.emit("join_conversation", conversationId);

            // Lắng nghe tin nhắn mới từ người khác
            socket.on("new_message", args -> {
                if (args.length > 0) {
                    JsonObject msgJson = new Gson().fromJson(args[0].toString(), JsonObject.class);
                    Message msg = new Gson().fromJson(msgJson, Message.class);
                    runOnUiThread(() -> {
                        adapter.addMessage(msg);
                        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                    });
                }
            });

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void loadMessageHistory() {
        ApiClient.getService().getMessages(conversationId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    if (body.get("success").getAsBoolean()) {
                        JsonArray data = body.getAsJsonArray("data");
                        Type listType = new TypeToken<List<Message>>() {}.getType();
                        List<Message> history = new Gson().fromJson(data, listType);
                        adapter.setMessages(history);
                        if (adapter.getItemCount() > 0) {
                            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Lỗi tải lịch sử chat: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;

            RequestBody convIdPart = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(conversationId));
            RequestBody contentPart = RequestBody.create(MediaType.parse("text/plain"), text);
            RequestBody typePart = RequestBody.create(MediaType.parse("text/plain"), "TEXT");

            etMessage.setText("");

            ApiClient.getService().sendMessage(convIdPart, contentPart, typePart, null).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject body = response.body();
                        if (body.get("success").getAsBoolean()) {
                            JsonObject data = body.getAsJsonObject("data");
                            Message sentMsg = new Gson().fromJson(data, Message.class);

                            // Hiển thị ngay lên màn hình
                            adapter.addMessage(sentMsg);
                            rvMessages.scrollToPosition(adapter.getItemCount() - 1);

                            // Bắn socket thông báo cho thành viên khác trong phòng
                            if (socket != null && socket.connected()) {
                                socket.emit("send_message", data);
                            }
                        }
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(ChatActivity.this, "Gửi tin nhắn thất bại: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            socket.emit("leave_conversation", conversationId);
            socket.disconnect();
        }
    }
}
