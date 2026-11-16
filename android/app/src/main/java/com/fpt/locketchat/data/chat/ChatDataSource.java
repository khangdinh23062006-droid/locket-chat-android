package com.fpt.locketchat.data.chat;

import com.fpt.locketchat.data.model.Message;
import java.io.File;
import java.util.List;

public interface ChatDataSource {
    interface Listener {
        void messages(List<Message> messages);
        void connection(boolean connected);
        void error(String message);
    }
    interface SendCallback {
        void progress(int percent);
        void success(Message message);
        void failure(String message);
    }
    void start(int conversationId, Listener listener);
    void refresh();
    void send(String text, String type, File file, String mime, String clientId, SendCallback callback);
    void markRead(int messageId);
    void close();
}
