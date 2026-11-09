package com.fpt.locketchat.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Conversation;
import com.fpt.locketchat.ui.profile.ProfileActivity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvConversations;
    private ConversationAdapter adapter;
    private EditText etSearchPhone;
    private Button btnSearch, btnProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        loadConversations();
        setupListeners();
    }

    private void initViews() {
        rvConversations = findViewById(R.id.rvConversations);
        etSearchPhone = findViewById(R.id.etSearchPhone);
        btnSearch = findViewById(R.id.btnSearch);
        btnProfile = findViewById(R.id.btnProfile);
    }

    private void setupRecyclerView() {
        adapter = new ConversationAdapter(this);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);
    }

    private void setupListeners() {
        // Mở màn hình Hồ sơ & Đa ngôn ngữ (ProfileActivity)
        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });

        // Tìm kiếm và thêm bạn bè bằng số điện thoại (Rubric 20 Điểm)
        btnSearch.setOnClickListener(v -> {
            String query = etSearchPhone.getText().toString().trim();
            if (query.isEmpty()) {
                loadConversations();
                return;
            }

            ApiClient.getService().searchByPhone(query).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject res = response.body();
                        JsonArray data = res.getAsJsonArray("data");
                        if (data.size() == 0) {
                            Toast.makeText(MainActivity.this, "Không tìm thấy người dùng với SĐT này", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Tìm thấy " + data.size() + " người dùng", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(MainActivity.this, "Lỗi tìm kiếm: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadConversations() {
        ApiClient.getService().getConversations().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    if (body.get("success").getAsBoolean()) {
                        JsonArray data = body.getAsJsonArray("data");
                        Gson gson = new Gson();
                        Type listType = new TypeToken<List<Conversation>>() {}.getType();
                        List<Conversation> conversations = gson.fromJson(data, listType);
                        adapter.setData(conversations);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Lỗi tải cuộc trò chuyện: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
    }
}
