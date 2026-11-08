package com.fpt.locketchat.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.ui.main.MainActivity;
import com.fpt.locketchat.utils.ValidationUtils;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etFullName, etPhone, etPassword;
    private Button btnLogin, btnToggleRegister;
    private ProgressBar progressBar;
    private boolean isRegisterMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra xem đã đăng nhập trước đó chưa
        SharedPreferences prefs = getSharedPreferences("locket_prefs", MODE_PRIVATE);
        String savedToken = prefs.getString("token", null);
        if (savedToken != null) {
            ApiClient.setAuthToken(savedToken);
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnToggleRegister = findViewById(R.id.btnToggleRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnToggleRegister.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            if (isRegisterMode) {
                etFullName.setVisibility(View.VISIBLE);
                btnLogin.setText(getString(R.string.btn_register));
                btnToggleRegister.setText(getString(R.string.btn_login));
            } else {
                etFullName.setVisibility(View.GONE);
                btnLogin.setText(getString(R.string.btn_login));
                btnToggleRegister.setText(getString(R.string.btn_register));
            }
        });

        btnLogin.setOnClickListener(v -> handleAuthAction());
    }

    private void handleAuthAction() {
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String fullName = etFullName.getText().toString().trim();

        if (isRegisterMode && !ValidationUtils.isNotEmpty(fullName)) {
            Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ValidationUtils.isValidPhoneNumber(phone)) {
            Toast.makeText(this, getString(R.string.error_invalid_phone), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ValidationUtils.isValidPassword(password)) {
            Toast.makeText(this, "Mật khẩu tối thiểu 6 ký tự!", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        JsonObject body = new JsonObject();
        body.addProperty("phoneNumber", phone);
        body.addProperty("password", password);
        if (isRegisterMode) {
            body.addProperty("fullName", fullName);
        }

        Call<JsonObject> call = isRegisterMode
                ? ApiClient.getService().register(body)
                : ApiClient.getService().login(body);

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject resBody = response.body();
                    if (resBody.get("success").getAsBoolean()) {
                        JsonObject data = resBody.getAsJsonObject("data");
                        String token = data.get("token").getAsString();
                        JsonObject user = data.getAsJsonObject("user");

                        // Lưu token & user vào local SharedPreferences
                        SharedPreferences.Editor editor = getSharedPreferences("locket_prefs", MODE_PRIVATE).edit();
                        editor.putString("token", token);
                        editor.putInt("user_id", user.get("id").getAsInt());
                        editor.putString("user_name", user.get("fullName").getAsString());
                        editor.putString("user_phone", user.get("phoneNumber").getAsString());
                        editor.apply();

                        ApiClient.setAuthToken(token);

                        Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, resBody.get("message").getAsString(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Đăng nhập thất bại. Kiểm tra lại tài khoản hoặc kết nối server!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnToggleRegister.setEnabled(!isLoading);
    }
}
