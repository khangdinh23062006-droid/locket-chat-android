package com.fpt.locketchat.ui.chat;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Message;
import com.fpt.locketchat.utils.FileUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.io.File;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
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
    private ImageButton btnAttach;
    private MessageAdapter adapter;
    private Socket socket;

    // Bộ chọn Tệp và Hình ảnh (Multimedia & File Picker)
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private boolean isPickingImage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getIntExtra("conversation_id", 0);
        displayName = getIntent().getStringExtra("display_name");

        SharedPreferences prefs = getSharedPreferences("locket_prefs", MODE_PRIVATE);
        currentUserId = prefs.getInt("user_id", 0);

        initViews();
        setupFilePicker();
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
        btnAttach = findViewById(R.id.btnAttach);

        if (displayName != null) {
            tvChatTitle.setText(displayName);
        }
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            uploadAndSendMessage(selectedUri, isPickingImage ? "IMAGE" : "FILE");
                        }
                    }
                }
        );
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
            socket = IO.socket("http://10.0.2.2:5000");
            socket.connect();

            socket.emit("join_conversation", conversationId);

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
        // Gửi tin nhắn Text
        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;

            RequestBody convIdPart = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(conversationId));
            RequestBody contentPart = RequestBody.create(MediaType.parse("text/plain"), text);
            RequestBody typePart = RequestBody.create(MediaType.parse("text/plain"), "TEXT");

            etMessage.setText("");

            sendRequest(convIdPart, contentPart, typePart, null);
        });

        // Bấm nút đính kèm (Gửi Multimedia hoặc File tài liệu - Rubric Nâng cao 15đ)
        btnAttach.setOnClickListener(v -> showAttachmentOptions());
    }

    private void showAttachmentOptions() {
        CharSequence[] options = new CharSequence[]{"Gửi Hình ảnh", "Gửi Tệp Tài liệu"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn nội dung gửi");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Chọn ảnh
                isPickingImage = true;
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                filePickerLauncher.launch(intent);
            } else {
                // Chọn file
                isPickingImage = false;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                filePickerLauncher.launch(intent);
            }
        });
        builder.show();
    }

    private void uploadAndSendMessage(Uri uri, String messageType) {
        Toast.makeText(this, "Đang tải lên...", Toast.LENGTH_SHORT).show();
        File file = FileUtils.getFileFromUri(this, uri);
        if (file == null) {
            Toast.makeText(this, "Không thể đọc file đã chọn", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestBody reqFile = RequestBody.create(MediaType.parse("*/*"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), reqFile);

        RequestBody convIdPart = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(conversationId));
        RequestBody contentPart = RequestBody.create(MediaType.parse("text/plain"), file.getName());
        RequestBody typePart = RequestBody.create(MediaType.parse("text/plain"), messageType);

        sendRequest(convIdPart, contentPart, typePart, filePart);
    }

    private void sendRequest(RequestBody convId, RequestBody content, RequestBody type, MultipartBody.Part filePart) {
        ApiClient.getService().sendMessage(convId, content, type, filePart).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    if (body.get("success").getAsBoolean()) {
                        JsonObject data = body.getAsJsonObject("data");
                        Message sentMsg = new Gson().fromJson(data, Message.class);

                        adapter.addMessage(sentMsg);
                        rvMessages.scrollToPosition(adapter.getItemCount() - 1);

                        if (socket != null && socket.connected()) {
                            socket.emit("send_message", data);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Gửi thất bại: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
