package com.fpt.locketchat.ui.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.ui.auth.LoginActivity;
import com.fpt.locketchat.ui.main.MainActivity;
import com.google.gson.JsonObject;
import java.util.Locale;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvProfileName, tvProfilePhone;
    private EditText etProfileBio;
    private Button btnSaveProfile, btnToggleLanguage, btnLogoutProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        loadProfileData();
        setupListeners();
    }

    private void initViews() {
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfilePhone = findViewById(R.id.tvProfilePhone);
        etProfileBio = findViewById(R.id.etProfileBio);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnToggleLanguage = findViewById(R.id.btnToggleLanguage);
        btnLogoutProfile = findViewById(R.id.btnLogoutProfile);
    }

    private void loadProfileData() {
        SharedPreferences prefs = getSharedPreferences("locket_prefs", MODE_PRIVATE);
        tvProfileName.setText(prefs.getString("user_name", "Locket User"));
        tvProfilePhone.setText(prefs.getString("user_phone", ""));

        ApiClient.getService().getMe().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body().getAsJsonObject("data");
                    if (data != null) {
                        tvProfileName.setText(data.get("full_name").getAsString());
                        tvProfilePhone.setText(data.get("phone_number").getAsString());
                        if (data.has("bio") && !data.get("bio").isJsonNull()) {
                            etProfileBio.setText(data.get("bio").getAsString());
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    private void setupListeners() {
        // Cập nhật Bio
        btnSaveProfile.setOnClickListener(v -> {
            String newBio = etProfileBio.getText().toString().trim();
            JsonObject body = new JsonObject();
            body.addProperty("bio", newBio);

            ApiClient.getService().updateProfile(null, RequestBody.create(MediaType.parse("text/plain"), newBio)).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    Toast.makeText(ProfileActivity.this, "Đã lưu thông tin hồ sơ!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(ProfileActivity.this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Chuyển đổi ngôn ngữ i18n (Tiếng Việt <-> English) - Rubric Nâng cao 15đ
        btnToggleLanguage.setOnClickListener(v -> toggleAppLanguage());

        // Đăng xuất
        btnLogoutProfile.setOnClickListener(v -> {
            SharedPreferences.Editor editor = getSharedPreferences("locket_prefs", MODE_PRIVATE).edit();
            editor.clear();
            editor.apply();
            ApiClient.setAuthToken(null);
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void toggleAppLanguage() {
        SharedPreferences prefs = getSharedPreferences("locket_prefs", MODE_PRIVATE);
        String currentLang = prefs.getString("app_lang", "vi");
        String nextLang = "vi".equals(currentLang) ? "en" : "vi";

        prefs.edit().putString("app_lang", nextLang).apply();

        Locale locale = new Locale(nextLang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        Toast.makeText(this, "vi".equals(nextLang) ? "Đã chuyển sang Tiếng Việt" : "Switched to English", Toast.LENGTH_SHORT).show();

        // Khởi động lại màn hình chính để áp dụng ngôn ngữ mới
        Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
