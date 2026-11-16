package com.fpt.locketchat.ui.chat;

import androidx.lifecycle.*;
import com.fpt.locketchat.data.chat.ChatDataSource;
import com.fpt.locketchat.data.model.Message;
import java.io.File;
import java.util.*;

/** Owns the conversation across rotation; no Activity or View references. */
public final class ChatViewModel extends ViewModel {
    private final ChatDataSource source;
    private final SavedStateHandle state;
    private final TreeMap<Integer, Message> byId = new TreeMap<>();
    private final MutableLiveData<List<Message>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> sending = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private boolean started, active, cleared, failed;
    private String pendingText, pendingType, pendingMime;
    private String clientId;
    private File pendingFile;

    public ChatViewModel(ChatDataSource source, SavedStateHandle state) { this.source = source; this.state = state; }
    public LiveData<List<Message>> messages() { return messages; }
    public LiveData<Boolean> connected() { return connected; }
    public LiveData<Boolean> sending() { return sending; }
    public LiveData<Integer> progress() { return progress; }
    public LiveData<String> error() { return error; }
    public String draft() { String draft = state.get("draft"); return draft == null ? "" : draft; }
    public void setDraft(String draft) { state.set("draft", draft); }
    public boolean hasFailedSend() { return failed; }
    public void start(int conversationId) {
        if (started) return;
        started = true;
        source.start(conversationId, new ChatDataSource.Listener() {
            @Override public void messages(List<Message> batch) {
                if (cleared) return;
                for (Message message : batch) byId.put(message.getId(), message);
                messages.setValue(new ArrayList<>(byId.values()));
                markVisible();
            }
            @Override public void connection(boolean value) {
                if (!cleared) { connected.setValue(value); if (value && !failed) error.setValue(""); }
            }
            @Override public void error(String value) { if (!cleared) error.setValue(value); }
        });
    }
    public void setActive(boolean value) { active = value; if (value) { markVisible(); } }
    private void markVisible() { if (active && !byId.isEmpty()) source.markRead(byId.lastKey()); }
    public void refresh() { source.refresh(); }
    public void sendText() {
        String text = draft().trim();
        if (text.isEmpty() || Boolean.TRUE.equals(sending.getValue())) return;
        failed = false;
        begin(text, "TEXT", null, null);
    }
    public void sendFile(File file, String type, String mime) {
        if (Boolean.TRUE.equals(sending.getValue())) return;
        failed = false;
        begin(file.getName(), type, file, mime);
    }
    private void begin(String text, String type, File file, String mime) {
        if (!failed) clientId = UUID.randomUUID().toString();
        if (pendingFile != null && pendingFile != file) pendingFile.delete();
        pendingText = text; pendingType = type; pendingFile = file; pendingMime = mime;
        failed = false; sending.setValue(true); progress.setValue(0); error.setValue("");
        source.send(text, type, file, mime, clientId, new ChatDataSource.SendCallback() {
            @Override public void progress(int percent) { if (!cleared) progress.setValue(percent); }
            @Override public void success(Message message) {
                if (cleared) return;
                byId.put(message.getId(), message);
                messages.setValue(new ArrayList<>(byId.values()));
                markVisible();
                if ("TEXT".equals(type) && draft().trim().equals(text)) setDraft("");
                if (file != null) file.delete();
                pendingFile = null; failed = false; sending.setValue(false);
            }
            @Override public void failure(String value) {
                if (cleared) return;
                failed = true; sending.setValue(false); error.setValue(value);
            }
        });
    }
    public void retry() {
        if (Boolean.TRUE.equals(sending.getValue())) return;
        if (failed) begin(pendingText, pendingType, pendingFile, pendingMime);
        else refresh();
    }
    @Override protected void onCleared() {
        cleared = true;
        source.close();
        if (pendingFile != null) pendingFile.delete();
    }
}
