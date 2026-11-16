package com.fpt.locketchat.ui.chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.chat.ChatRepository;
import com.fpt.locketchat.utils.FileUtils;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {
    private ChatViewModel model;
    private EditText input;
    private TextView status, error;
    private ProgressBar progress;
    private Button send;
    private ImageButton attach, mic;
    private MessageAdapter adapter;
    private RecyclerView recycler;
    private MediaRecorder recorder;
    private File recording;
    private boolean copying;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importFile(uri); });
    private final ActivityResultLauncher<String> audioPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    Toast.makeText(this, granted ? "Nhấn giữ micro để ghi âm" : "Cần quyền micro để ghi âm", Toast.LENGTH_SHORT).show());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_chat);
        int conversationId = getIntent().getIntExtra("conversation_id", 0);
        if (conversationId <= 0) { finish(); return; }
        int userId = getSharedPreferences("locket_prefs", MODE_PRIVATE).getInt("user_id", 0);
        model = new ViewModelProvider(this, new AbstractSavedStateViewModelFactory(this, state) {
            @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> cls,
                                                                         @NonNull SavedStateHandle handle) {
                return cls.cast(new ChatViewModel(new ChatRepository(getApplicationContext(), userId), handle));
            }
        }).get(ChatViewModel.class);
        ((TextView) findViewById(R.id.tvChatTitle)).setText(getIntent().getStringExtra("display_name"));
        input = findViewById(R.id.etMessage);
        send = findViewById(R.id.btnSend);
        attach = findViewById(R.id.btnAttach);
        mic = findViewById(R.id.btnMic);
        progress = findViewById(R.id.uploadProgress);
        status = findViewById(R.id.tvConnection);
        error = findViewById(R.id.tvChatError);
        recycler = findViewById(R.id.rvMessages);
        adapter = new MessageAdapter(this, userId);
        LinearLayoutManager layout = new LinearLayoutManager(this);
        layout.setStackFromEnd(true);
        recycler.setLayoutManager(layout);
        recycler.setAdapter(adapter);
        input.setText(model.draft());
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { model.setDraft(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
        model.messages().observe(this, messages -> {
            int previous = adapter.getItemCount();
            adapter.setMessages(messages);
            if (previous == 0 || messages.size() > previous) recycler.scrollToPosition(Math.max(0, messages.size() - 1));
        });
        model.sending().observe(this, sending -> {
            progress.setVisibility(sending ? View.VISIBLE : View.GONE);
            updateButtons(sending);
            if (!sending && !input.getText().toString().equals(model.draft())) input.setText(model.draft());
        });
        model.progress().observe(this, value -> progress.setProgress(value));
        model.connected().observe(this, connected -> status.setText(connected ? "Đã kết nối" : "Offline • đang xem tin đã lưu"));
        model.error().observe(this, message -> {
            error.setText(message);
            error.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
        });
        send.setOnClickListener(v -> model.sendText());
        attach.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Đính kèm")
                .setItems(new String[]{"Hình ảnh", "Video", "Tài liệu (PDF, DOCX, ZIP)"}, (dialog, which) ->
                        picker.launch(which == 0 ? new String[]{"image/*"} : which == 1 ? new String[]{"video/*"}
                                : new String[]{"application/pdf", "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/zip", "application/x-zip-compressed", "application/octet-stream"})).show());
        findViewById(R.id.btnRetry).setOnClickListener(v -> model.retry());
        mic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    audioPermission.launch(Manifest.permission.RECORD_AUDIO);
                else startRecording();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopRecording(event.getAction() == MotionEvent.ACTION_UP);
                v.performClick();
                return true;
            }
            return true;
        });
        model.start(conversationId);
    }
    private void updateButtons(boolean sending) {
        send.setEnabled(!sending && !copying && recorder == null);
        attach.setEnabled(!sending && !copying && recorder == null);
        mic.setEnabled(!sending && !copying);
    }
    private void importFile(Uri uri) {
        if (Boolean.TRUE.equals(model.sending().getValue()) || copying) return;
        copying = true; updateButtons(false);
        String mime = getContentResolver().getType(uri);
        files.execute(() -> {
            File file = FileUtils.getFileFromUri(this, uri);
            runOnUiThread(() -> {
                copying = false;
                if (isDestroyed()) { if (file != null) file.delete(); return; }
                updateButtons(Boolean.TRUE.equals(model.sending().getValue()));
                if (file == null) { Toast.makeText(this, "Không đọc được tệp (tối đa 50 MB)", Toast.LENGTH_LONG).show(); return; }
                String type = mime != null && mime.startsWith("image/") ? "IMAGE"
                        : mime != null && mime.startsWith("video/") ? "VIDEO"
                        : mime != null && mime.startsWith("audio/") ? "AUDIO" : "FILE";
                model.sendFile(file, type, mime);
            });
        });
    }
    @SuppressWarnings("deprecation")
    private void startRecording() {
        if (recorder != null || copying || Boolean.TRUE.equals(model.sending().getValue())) return;
        try {
            recording = File.createTempFile("voice_", ".aac", getCacheDir());
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recording.getAbsolutePath());
            recorder.setMaxDuration(120000);
            recorder.setOnInfoListener((r, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording(true);
            });
            recorder.prepare(); recorder.start();
            status.setText("Đang ghi âm • thả để gửi (tối đa 2 phút)");
            updateButtons(false);
        } catch (Exception e) {
            stopRecording(false);
            Toast.makeText(this, "Không thể ghi âm", Toast.LENGTH_SHORT).show();
        }
    }
    private void stopRecording(boolean sendAudio) {
        if (recorder == null) return;
        try { recorder.stop(); } catch (RuntimeException e) { sendAudio = false; }
        recorder.release(); recorder = null;
        status.setText(Boolean.TRUE.equals(model.connected().getValue()) ? "Đã kết nối" : "Offline");
        updateButtons(Boolean.TRUE.equals(model.sending().getValue()));
        if (recording != null) {
            if (sendAudio && recording.length() > 0) model.sendFile(recording, "AUDIO", "audio/aac");
            else recording.delete();
        }
        recording = null;
    }
    @Override protected void onResume() { super.onResume(); if (model != null) model.setActive(true); }
    @Override protected void onPause() {
        if (model != null) model.setActive(false);
        stopRecording(false);
        if (adapter != null) adapter.releasePlayer();
        super.onPause();
    }
    @Override protected void onDestroy() {
        files.shutdown();
        if (adapter != null) adapter.releasePlayer();
        super.onDestroy();
    }
}
