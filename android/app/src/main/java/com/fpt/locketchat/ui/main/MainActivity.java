package com.fpt.locketchat.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Conversation;
import com.fpt.locketchat.data.model.Moment;
import com.fpt.locketchat.ui.profile.ProfileActivity;
import com.fpt.locketchat.utils.FileUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutionException;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    // Views Container
    private RelativeLayout layoutCameraMode, layoutMomentsMode;
    private LinearLayout layoutChatMode;

    // Top Header Views
    private ImageView btnTopProfile;
    private LinearLayout pillFriendsCount;
    private TextView tvFriendsCount, tvFriendsBadge;

    // Camera Views
    private PreviewView cameraPreview;
    private ImageView ivCapturedPhoto;
    private LinearLayout layoutCaption;
    private EditText etMomentCaption;
    private Button btnSendMoment;
    private ImageView btnShutter;
    private ImageButton btnFlipCamera, btnGalleryPicker;
    private LinearLayout layoutHistoryPill;
    private ImageView ivHistoryThumb;

    // Moments Feed Views
    private ViewPager2 vpMomentsFeed;
    private MomentFeedAdapter momentAdapter;
    private LinearLayout layoutEmptyMoments;
    private Integer selectedFilterUserId = null;
    private String selectedFilterName = "Mọi người";

    // Chat Views
    private RecyclerView rvConversations;
    private ConversationAdapter adapter;
    private EditText etSearchPhone;
    private Button btnSearch;

    // Bottom Dock Views
    private ImageButton btnTabHistory, btnTabCamera, btnTabChat;
    private FrameLayout btnTabCameraWrapper;
    private TextView tvChatBadge;

    // CameraX Objects
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private boolean isBackCamera = true;
    private File capturedFile = null;

    // Permission Launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Cần cấp quyền Camera để chụp ảnh Locket", Toast.LENGTH_SHORT).show();
                }
            });

    // Gallery Picker Launcher (Hỗ trợ máy ảo không có Camera hoặc chọn ảnh từ thư viện)
    private final ActivityResultLauncher<String> galleryPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        File tempFile = new File(getCacheDir(), "picked_moment_" + System.currentTimeMillis() + ".jpg");
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        FileOutputStream outputStream = new FileOutputStream(tempFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.close();
                        inputStream.close();

                        capturedFile = tempFile;
                        ivCapturedPhoto.setImageURI(uri);
                        ivCapturedPhoto.setVisibility(View.VISIBLE);
                        cameraPreview.setVisibility(View.GONE);
                        layoutCaption.setVisibility(View.VISIBLE);
                        etMomentCaption.setText("");
                    } catch (Exception e) {
                        Toast.makeText(this, "Không thể đọc ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        loadConversations();
        loadFriendsCount();
        loadLatestHistoryThumbnail();
        loadLatestHistoryThumbnail();
        setupNavigationTabs();
        setupCamera();
        setupChatActions();
    }

    private void initViews() {
        layoutCameraMode = findViewById(R.id.layoutCameraMode);
        layoutMomentsMode = findViewById(R.id.layoutMomentsMode);
        layoutChatMode = findViewById(R.id.layoutChatMode);

        btnTopProfile = findViewById(R.id.btnTopProfile);
        pillFriendsCount = findViewById(R.id.pillFriendsCount);
        tvFriendsCount = findViewById(R.id.tvFriendsCount);
        tvFriendsBadge = findViewById(R.id.tvFriendsBadge);

        cameraPreview = findViewById(R.id.cameraPreview);
        ivCapturedPhoto = findViewById(R.id.ivCapturedPhoto);
        layoutCaption = findViewById(R.id.layoutCaption);
        etMomentCaption = findViewById(R.id.etMomentCaption);
        btnSendMoment = findViewById(R.id.btnSendMoment);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnGalleryPicker = findViewById(R.id.btnGalleryPicker);
        layoutHistoryPill = findViewById(R.id.layoutHistoryPill);
        ivHistoryThumb = findViewById(R.id.ivHistoryThumb);

        // Setup Moments Feed ViewPager2 (Vuốt dọc từng khoảnh khắc chuẩn Locket)
        vpMomentsFeed = findViewById(R.id.vpMomentsFeed);
        layoutEmptyMoments = findViewById(R.id.layoutEmptyMoments);
        momentAdapter = new MomentFeedAdapter(this);
        vpMomentsFeed.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        vpMomentsFeed.setAdapter(momentAdapter);

        // Lắng nghe bình luận và thả cảm xúc React trên khoảnh khắc
        momentAdapter.setListener(new MomentFeedAdapter.OnMomentActionListener() {
            @Override
            public void onSendComment(Moment moment, String comment) {
                sendMomentMessage(moment, comment);
            }

            @Override
            public void onSendReaction(Moment moment, String reactionEmoji) {
                sendMomentMessage(moment, reactionEmoji);
            }
        });

        rvConversations = findViewById(R.id.rvConversations);
        etSearchPhone = findViewById(R.id.etSearchPhone);
        btnSearch = findViewById(R.id.btnSearch);

        btnTabHistory = findViewById(R.id.btnTabHistory);
        btnTabCamera = findViewById(R.id.btnTabCamera);
        btnTabCameraWrapper = findViewById(R.id.btnTabCameraWrapper);
        btnTabChat = findViewById(R.id.btnTabChat);
        tvChatBadge = findViewById(R.id.tvChatBadge);
    }

    private void setupNavigationTabs() {
        // Tab Camera (Chính)
        btnTabCamera.setOnClickListener(v -> showCameraMode());

        // Tab Chat (Nhắn tin - mục nhỏ phía dưới)
        btnTabChat.setOnClickListener(v -> showChatMode());

        // Tab Lịch sử / Khoảnh khắc (Trái) & Viên thuốc Lịch sử
        if (btnTabHistory != null) {
            btnTabHistory.setOnClickListener(v -> showMomentsMode());
        }
        if (layoutHistoryPill != null) {
            layoutHistoryPill.setOnClickListener(v -> showMomentsMode());
        }

        // Nút Profile (Góc phải trên)
        btnTopProfile.setOnClickListener(v -> openProfile());

        // Nút xem bạn bè / Lọc khoảnh khắc
        pillFriendsCount.setOnClickListener(v -> {
            if (layoutMomentsMode != null && layoutMomentsMode.getVisibility() == View.VISIBLE) {
                showFilterDialog();
            } else {
                showFriendsDialog();
            }
        });
    }

    private void showCameraMode() {
        layoutCameraMode.setVisibility(View.VISIBLE);
        layoutMomentsMode.setVisibility(View.GONE);
        layoutChatMode.setVisibility(View.GONE);
        if (btnTabCameraWrapper != null) {
            btnTabCameraWrapper.setBackgroundResource(R.drawable.bg_dock_active_circle);
        }
        btnTabChat.clearColorFilter();
        btnTabHistory.clearColorFilter();
        loadFriendsCount();
        if (tvFriendsBadge != null) tvFriendsBadge.setVisibility(View.VISIBLE);
        startCamera();
    }

    private void showMomentsMode() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        layoutCameraMode.setVisibility(View.GONE);
        layoutMomentsMode.setVisibility(View.VISIBLE);
        layoutChatMode.setVisibility(View.GONE);
        btnTabHistory.setColorFilter(getColor(R.color.primary));
        if (btnTabCameraWrapper != null) {
            btnTabCameraWrapper.setBackground(null);
        }
        btnTabChat.clearColorFilter();
        
        // Hiển thị nhãn lọc "Mọi người ⌄"
        tvFriendsCount.setText(selectedFilterName + " ⌄");
        if (tvFriendsBadge != null) tvFriendsBadge.setVisibility(View.GONE);

        loadFeedMoments();
    }

    private void showChatMode() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        layoutCameraMode.setVisibility(View.GONE);
        layoutMomentsMode.setVisibility(View.GONE);
        layoutChatMode.setVisibility(View.VISIBLE);
        if (btnTabCameraWrapper != null) {
            btnTabCameraWrapper.setBackground(null);
        }
        btnTabChat.setColorFilter(getColor(R.color.primary));
        btnTabHistory.clearColorFilter();
        loadConversations();
    }

    private void openProfile() {
        startActivity(new Intent(MainActivity.this, ProfileActivity.class));
    }

    private void showFriendsDialog() {
        ApiClient.getService().getFriends().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray friends = response.body().getAsJsonArray("data");
                    if (friends == null || friends.size() == 0) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Danh sách bạn bè")
                                .setMessage("Bạn chưa có bạn bè nào. Hãy tìm kiếm bạn bằng số điện thoại ở mục Tin nhắn!")
                                .setPositiveButton("Đóng", null)
                                .show();
                        return;
                    }

                    String[] names = new String[friends.size()];
                    for (int i = 0; i < friends.size(); i++) {
                        JsonObject f = friends.get(i).getAsJsonObject();
                        String name = f.has("full_name") ? f.get("full_name").getAsString() : "Bạn bè";
                        String phone = f.has("phone") ? f.get("phone").getAsString() : "";
                        names[i] = "👤 " + name + " (" + phone + ")";
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Bạn bè (" + friends.size() + ")")
                            .setItems(names, null)
                            .setPositiveButton("Đóng", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    // Dropdown chọn người để lọc xem khoảnh khắc (Chuẩn hình ảnh Locket)
    private void showFilterDialog() {
        ApiClient.getService().getFriends().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray friends = response.body().getAsJsonArray("data");
                    int friendCount = friends != null ? friends.size() : 0;

                    // Danh sách lựa chọn lọc: 1. Mọi người, 2. Bạn, 3..N: Từng người bạn
                    String[] filterItems = new String[2 + friendCount];
                    filterItems[0] = "👥 Mọi người";
                    filterItems[1] = "👤 Bạn (Chỉ khoảnh khắc của tôi)";

                    for (int i = 0; i < friendCount; i++) {
                        JsonObject f = friends.get(i).getAsJsonObject();
                        String name = f.has("full_name") ? f.get("full_name").getAsString() : "Bạn bè";
                        filterItems[i + 2] = "👤 " + name;
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Lọc khoảnh khắc theo người")
                            .setItems(filterItems, (dialog, which) -> {
                                if (which == 0) {
                                    selectedFilterUserId = null;
                                    selectedFilterName = "Mọi người";
                                } else if (which == 1) {
                                    int myId = getSharedPreferences("locket_prefs", MODE_PRIVATE).getInt("user_id", -1);
                                    selectedFilterUserId = myId;
                                    selectedFilterName = "Bạn";
                                } else {
                                    JsonObject f = friends.get(which - 2).getAsJsonObject();
                                    selectedFilterUserId = f.has("id") ? f.get("id").getAsInt() : null;
                                    selectedFilterName = f.has("full_name") ? f.get("full_name").getAsString() : "Bạn bè";
                                }
                                tvFriendsCount.setText(selectedFilterName + " ⌄");
                                loadFeedMoments();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    private void loadFeedMoments() {
        ApiClient.getService().getFeedMoments(selectedFilterUserId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    if (body.get("success").getAsBoolean()) {
                        JsonArray data = body.getAsJsonArray("data");
                        Type listType = new TypeToken<List<Moment>>() {}.getType();
                        List<Moment> moments = new Gson().fromJson(data, listType);
                        momentAdapter.setData(moments);

                        // Cập nhật ảnh đại diện khoảnh khắc mới nhất vào nút Lịch sử (Chuẩn Ảnh 2)
                        if (moments != null && !moments.isEmpty()) {
                            updateHistoryThumbnail(moments.get(0).getImageUrl());
                        }

                        if (moments == null || moments.isEmpty()) {
                            layoutEmptyMoments.setVisibility(View.VISIBLE);
                        } else {
                            layoutEmptyMoments.setVisibility(View.GONE);
                            if (vpMomentsFeed != null) {
                                vpMomentsFeed.setCurrentItem(0, false);
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                layoutEmptyMoments.setVisibility(View.VISIBLE);
            }
        });
    }

    // Cập nhật ảnh khoảnh khắc mới nhất thu nhỏ vào nút Lịch sử (Chuẩn Ảnh 2)
    private void updateHistoryThumbnail(String imageUrl) {
        if (ivHistoryThumb == null || imageUrl == null || imageUrl.trim().isEmpty()) return;
        String fullUrl = imageUrl;
        if (!fullUrl.startsWith("http")) {
            fullUrl = ApiClient.BASE_URL.replace("/api/", "") + fullUrl;
        }
        Glide.with(MainActivity.this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(ivHistoryThumb);
    }

    private void loadLatestHistoryThumbnail() {
        ApiClient.getService().getLatestWidgetMoment().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    if (body.has("data") && !body.get("data").isJsonNull()) {
                        JsonObject data = body.getAsJsonObject("data");
                        if (data.has("image_url")) {
                            updateHistoryThumbnail(data.get("image_url").getAsString());
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    // Gửi bình luận hoặc cảm xúc React vào phòng chat của người đăng khoảnh khắc (Chuẩn Ảnh 1)
    private void sendMomentMessage(Moment moment, String messageText) {
        int targetUserId = moment.getSenderId();
        int myUserId = getSharedPreferences("locket_prefs", MODE_PRIVATE).getInt("user_id", -1);

        if (targetUserId == myUserId || targetUserId <= 0) {
            Toast.makeText(this, "Đây là khoảnh khắc của chính bạn!", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObject directBody = new JsonObject();
        directBody.addProperty("targetUserId", targetUserId);

        ApiClient.getService().getOrCreateDirectChat(directBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject resBody = response.body();
                    if (resBody.get("success").getAsBoolean()) {
                        int convId = resBody.getAsJsonObject("data").get("conversationId").getAsInt();

                        // Gửi tin nhắn dạng MOMENT đính kèm ảnh khoảnh khắc và lời bình luận
                        JsonObject msgBody = new JsonObject();
                        msgBody.addProperty("conversationId", convId);
                        msgBody.addProperty("content", messageText);
                        msgBody.addProperty("messageType", "MOMENT");
                        msgBody.addProperty("fileUrl", moment.getImageUrl());

                        ApiClient.getService().sendMessageJson(msgBody).enqueue(new Callback<JsonObject>() {
                            @Override
                            public void onResponse(Call<JsonObject> c, Response<JsonObject> r) {
                                Toast.makeText(MainActivity.this, "Đã gửi vào chat của " + moment.getSenderName() + ": " + messageText, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(Call<JsonObject> c, Throwable t) {}
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // CAMERAX & LOCKET PHOTO CAPTURE
    // ==========================================
    private void setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        // Chuyển đổi Camera Trước / Sau
        btnFlipCamera.setOnClickListener(v -> {
            isBackCamera = !isBackCamera;
            startCamera();
        });

        // Chọn ảnh từ Thư viện (Hỗ trợ máy ảo không có Webcam hoặc muốn dùng ảnh có sẵn)
        btnGalleryPicker.setOnClickListener(v -> galleryPickerLauncher.launch("image/*"));

        // Bấm nút chụp Locket to tròn viền vàng
        btnShutter.setOnClickListener(v -> takePhoto());

        // Gửi khoảnh khắc Locket
        btnSendMoment.setOnClickListener(v -> sendMoment());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(new android.util.Size(720, 720))
                        .build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(new android.util.Size(1080, 1080))
                        .build();

                CameraSelector cameraSelector = isBackCamera
                        ? CameraSelector.DEFAULT_BACK_CAMERA
                        : CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        capturedFile = new File(getCacheDir(), "locket_moment_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(capturedFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Hiển thị ảnh vừa chụp lên khung xem trước
                Bitmap bitmap = BitmapFactory.decodeFile(capturedFile.getAbsolutePath());
                ivCapturedPhoto.setImageBitmap(bitmap);
                ivCapturedPhoto.setVisibility(View.VISIBLE);
                layoutCaption.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Đã chụp ảnh! Thêm ghi chú và bấm Gửi", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "Chụp ảnh lỗi: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMoment() {
        if (capturedFile == null || !capturedFile.exists()) {
            Toast.makeText(this, "Vui lòng chụp ảnh trước khi gửi", Toast.LENGTH_SHORT).show();
            return;
        }

        String caption = etMomentCaption.getText().toString().trim();
        RequestBody reqImage = RequestBody.create(MediaType.parse("image/jpeg"), capturedFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", capturedFile.getName(), reqImage);
        RequestBody captionPart = RequestBody.create(MediaType.parse("text/plain"), caption);

        Toast.makeText(this, "Đang gửi khoảnh khắc Locket...", Toast.LENGTH_SHORT).show();

        ApiClient.getService().postMoment(imagePart, captionPart).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Đã gửi khoảnh khắc Locket thành công!", Toast.LENGTH_SHORT).show();
                    // Reset về trạng thái Camera ban đầu
                    ivCapturedPhoto.setVisibility(View.GONE);
                    cameraPreview.setVisibility(View.VISIBLE);
                    layoutCaption.setVisibility(View.GONE);
                    etMomentCaption.setText("");
                    capturedFile = null;
                    loadFeedMoments();
                } else {
                    Toast.makeText(MainActivity.this, "Gửi khoảnh khắc thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==========================================
    // CHAT & CONVERSATIONS
    // ==========================================
    private void setupRecyclerView() {
        adapter = new ConversationAdapter(this);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);
    }

    private void setupChatActions() {
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
                        Toast.makeText(MainActivity.this, "Tìm thấy " + data.size() + " người dùng", Toast.LENGTH_SHORT).show();
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
                        Type listType = new TypeToken<List<Conversation>>() {}.getType();
                        List<Conversation> conversations = new Gson().fromJson(data, listType);
                        adapter.setData(conversations);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    private void loadFriendsCount() {
        ApiClient.getService().getFriends().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonArray friends = response.body().getAsJsonArray("data");
                    int count = friends != null ? friends.size() : 0;
                    tvFriendsCount.setText("👥 " + count + " người bạn");
                    if (tvFriendsBadge != null) {
                        tvFriendsBadge.setText(String.valueOf(count));
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
        loadFriendsCount();
        loadLatestHistoryThumbnail();
        if (layoutCameraMode != null && layoutCameraMode.getVisibility() == View.VISIBLE) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
