package com.fpt.locketchat.ui.chat;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.fpt.locketchat.data.chat.ChatDataSource;
import com.fpt.locketchat.data.model.Message;
import com.google.gson.Gson;
import org.junit.*;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

public class ChatViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private FakeSource source;
    private ChatViewModel model;
    @Before public void setup() { source = new FakeSource(); model = new ChatViewModel(source, new SavedStateHandle()); model.start(7); }
    private Message message(int id) {
        return new Gson().fromJson("{\"id\":" + id + ",\"conversation_id\":7,\"message_type\":\"TEXT\"}", Message.class);
    }
    @Test public void repeatedStartDoesNotReloadOnRotation() {
        source.listener.messages(Collections.singletonList(message(1)));
        model.setDraft("đang nhập");
        model.start(7);
        assertEquals(1, source.starts);
        assertEquals(1, model.messages().getValue().size());
        assertEquals("đang nhập", model.draft());
    }
    @Test public void historyCannotEraseRealtimeAndDuplicateIdsAreMerged() {
        source.listener.messages(Collections.singletonList(message(3)));
        source.listener.messages(Arrays.asList(message(1), message(2)));
        source.listener.messages(Collections.singletonList(message(3)));
        List<Message> result = model.messages().getValue();
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).getId());
        assertEquals(3, result.get(2).getId());
    }
    @Test public void failedSendKeepsDraftAndRetries() {
        model.setDraft("Xin chào"); model.sendText();
        source.callback.failure("offline");
        assertEquals("Xin chào", model.draft());
        assertTrue(model.hasFailedSend());
        model.retry();
        assertEquals(2, source.sends);
        assertEquals(source.keys.get(0), source.keys.get(1));
        source.callback.success(message(1));
        assertEquals("", model.draft());
        assertFalse(model.hasFailedSend());
    }
    @Test public void senderEchoAndApiResponseDoNotDuplicate() {
        model.setDraft("hello"); model.sendText();
        source.listener.messages(Collections.singletonList(message(1)));
        source.callback.success(message(1));
        assertEquals(1, model.messages().getValue().size());
    }
    @Test public void editingAndSendingAfterFailureCreatesANewMessageKey() {
        model.setDraft("first"); model.sendText();
        source.callback.failure("offline");
        model.setDraft("second"); model.sendText();
        assertNotEquals(source.keys.get(0), source.keys.get(1));
    }
    @Test public void inFlightSendDoesNotClearNewDraftOrAllowDoubleTap() {
        model.setDraft("first"); model.sendText(); model.sendText();
        assertEquals(1, source.sends);
        model.setDraft("second");
        source.callback.success(message(1));
        assertEquals("second", model.draft());
    }
    @Test public void onlyVisibleConversationIsMarkedRead() {
        source.listener.messages(Collections.singletonList(message(2)));
        assertEquals(0, source.readId);
        model.setActive(true);
        assertEquals(2, source.readId);
        model.setActive(false);
        source.listener.messages(Collections.singletonList(message(3)));
        assertEquals(2, source.readId);
    }
    @Test public void savedDraftIsRestoredAndResourcesClose() {
        Map<String, Object> saved = new HashMap<>(); saved.put("draft", "bản nháp");
        ChatViewModel restored = new ChatViewModel(source, new SavedStateHandle(saved));
        assertEquals("bản nháp", restored.draft());
        model.onCleared();
        assertTrue(source.closed);
    }
    @Test public void progressAndRefreshAreExposed() {
        model.setDraft("hello"); model.sendText();
        source.callback.progress(65);
        assertEquals(Integer.valueOf(65), model.progress().getValue());
        source.callback.failure("offline");
        model.refresh();
        assertEquals(1, source.refreshes);
    }
    private static class FakeSource implements ChatDataSource {
        int starts, sends, readId, refreshes;
        boolean closed;
        Listener listener;
        SendCallback callback;
        List<String> keys = new ArrayList<>();
        public void start(int id, Listener listener) { starts++; this.listener = listener; }
        public void refresh() { refreshes++; }
        public void send(String text, String type, File file, String mime, String clientId, SendCallback callback) {
            sends++; this.callback = callback; keys.add(clientId);
        }
        public void markRead(int id) { readId = id; }
        public void close() { closed = true; }
    }
}
