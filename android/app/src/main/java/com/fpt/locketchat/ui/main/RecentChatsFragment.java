package com.fpt.locketchat.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.local.ChatStore;
import com.fpt.locketchat.data.model.*;
import com.fpt.locketchat.ui.chat.ChatActivity;
import com.google.gson.*;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.util.*;
import java.util.concurrent.*;
import retrofit2.*;

public class RecentChatsFragment extends Fragment {
    private ConversationAdapter adapter;
    private TextView status;
    private ChatStore store;
    private ExecutorService disk;
    private Socket socket;
    private Call<JsonObject> request;
    private boolean loaded, reload;
    private int generation;
    private final Gson gson = new Gson();

    public RecentChatsFragment() { super(R.layout.fragment_recent_chats); }
    @Override public void onViewCreated(@NonNull View view, Bundle savedState) {
        adapter = new ConversationAdapter(requireContext());
        RecyclerView list = view.findViewById(R.id.rvConversations);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        status = view.findViewById(R.id.tvRecentStatus);
        int userId = requireContext().getSharedPreferences("locket_prefs", 0).getInt("user_id", 0);
        store = new ChatStore(requireContext(), userId);
        disk = Executors.newSingleThreadExecutor();
        int version = ++generation;
        disk.execute(() -> {
            try {
                List<Conversation> cached = store.conversations();
                view.post(() -> {
                    if (getView() != null && generation == version && !loaded) {
                        adapter.setData(cached);
                        updateBadge(cached);
                        status.setText("Tin đã lưu • đang đồng bộ");
                    }
                });
            } catch (RuntimeException ignored) {}
        });
        view.findViewById(R.id.btnNewGroup).setOnClickListener(v -> chooseFriends(true));
        view.findViewById(R.id.btnNewDirect).setOnClickListener(v -> chooseFriends(false));
        view.findViewById(R.id.btnRefreshChats).setOnClickListener(v -> refresh());
    }
    @Override public void onStart() {
        super.onStart();
        try {
            IO.Options options = new IO.Options();
            options.auth = Collections.singletonMap("token", ApiClient.getAuthToken());
            socket = IO.socket(ApiClient.getBaseHost(), options);
            socket.on(Socket.EVENT_CONNECT, args -> onMain(this::refresh));
            socket.on("conversation_changed", args -> onMain(this::refresh));
            socket.on(Socket.EVENT_DISCONNECT, args -> onMain(() -> status.setText("Offline • tin đã lưu")));
            socket.connect();
        } catch (Exception ignored) {}
        refresh();
    }
    private void onMain(Runnable action) {
        View view = getView();
        int version = generation;
        if (view != null) view.post(() -> { if (getView() != null && generation == version) action.run(); });
    }
    public void refresh() {
        if (getView() == null) return;
        if (request != null) { reload = true; return; }
        int version = generation;
        request = ApiClient.getService().getConversations();
        request.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (getView() == null || version != generation) return;
                request = null;
                if (response.isSuccessful() && response.body() != null) {
                    List<Conversation> data = Arrays.asList(gson.fromJson(response.body().get("data"), Conversation[].class));
                    loaded = true;
                    adapter.setData(data);
                    updateBadge(data);
                    status.setText(data.isEmpty() ? "Chưa có hội thoại. Chọn Nhắn tin hoặc Tạo nhóm." : "Đã cập nhật");
                    disk.execute(() -> { try { store.saveConversations(data); } catch (RuntimeException ignored) {} });
                } else status.setText("Không tải được hội thoại (" + response.code() + ")");
                if (reload) { reload = false; refresh(); }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable error) {
                if (getView() == null || version != generation) return;
                request = null; reload = false;
                status.setText("Offline • đang xem hội thoại đã lưu");
            }
        });
    }
    private void updateBadge(List<Conversation> conversations) {
        int unread = 0;
        for (Conversation c : conversations) unread += c.getUnreadCount();
        TextView badge = requireActivity().findViewById(R.id.tvChatBadge);
        if (badge != null) {
            badge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            badge.setVisibility(unread == 0 ? View.GONE : View.VISIBLE);
        }
    }
    private void chooseFriends(boolean group) {
        ApiClient.getService().getFriends().enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (getView() == null || !isAdded()) return;
                if (!response.isSuccessful() || response.body() == null) { toast("Không tải được danh bạ"); return; }
                User[] friends = gson.fromJson(response.body().get("data"), User[].class);
                if (friends.length < (group ? 2 : 1)) { toast(group ? "Cần ít nhất 2 bạn để tạo nhóm" : "Hãy thêm bạn trước"); return; }
                String[] names = new String[friends.length];
                for (int i = 0; i < names.length; i++) names[i] = friends[i].getFullName();
                if (!group) {
                    new AlertDialog.Builder(requireContext()).setTitle("Chọn bạn để nhắn tin")
                            .setItems(names, (dialog, index) -> {
                                JsonObject body = new JsonObject(); body.addProperty("targetUserId", friends[index].getId());
                                openRequest(ApiClient.getService().getOrCreateDirectChat(body), names[index]);
                            }).show();
                    return;
                }
                boolean[] selected = new boolean[friends.length];
                AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Chọn thành viên nhóm")
                        .setMultiChoiceItems(names, selected, (d, index, checked) -> selected[index] = checked)
                        .setNegativeButton("Hủy", null).setPositiveButton("Tiếp tục", null).create();
                dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    JsonArray ids = new JsonArray();
                    for (int i = 0; i < friends.length; i++) if (selected[i]) ids.add(friends[i].getId());
                    if (ids.size() < 2) { toast("Chọn ít nhất 2 bạn"); return; }
                    dialog.dismiss();
                    nameGroup(ids);
                }));
                dialog.show();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable error) { if (isAdded()) toast("Không có kết nối để tải danh bạ"); }
        });
    }
    private void nameGroup(JsonArray ids) {
        EditText name = new EditText(requireContext());
        name.setHint("Tên nhóm (tối đa 100 ký tự)");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Đặt tên nhóm").setView(name)
                .setNegativeButton("Hủy", null).setPositiveButton("Tạo nhóm", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError("Nhập tên nhóm"); return; }
            JsonObject body = new JsonObject(); body.addProperty("title", title); body.add("memberIds", ids);
            dialog.dismiss();
            openRequest(ApiClient.getService().createGroupChat(body), title);
        }));
        dialog.show();
    }
    private void openRequest(Call<JsonObject> call, String title) {
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> c, Response<JsonObject> response) {
                if (!isAdded() || getView() == null) return;
                if (!response.isSuccessful() || response.body() == null) { toast("Không tạo được phòng (" + response.code() + ")"); return; }
                int id = response.body().getAsJsonObject("data").get("conversationId").getAsInt();
                startActivity(new Intent(requireContext(), ChatActivity.class).putExtra("conversation_id", id).putExtra("display_name", title));
                refresh();
            }
            @Override public void onFailure(Call<JsonObject> c, Throwable e) { if (isAdded()) toast("Không kết nối được máy chủ"); }
        });
    }
    private void toast(String text) { Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show(); }
    @Override public void onStop() {
        if (socket != null) { socket.off(); socket.disconnect(); socket = null; }
        super.onStop();
    }
    @Override public void onDestroyView() {
        generation++;
        if (request != null) { request.cancel(); request = null; }
        ChatStore oldStore = store;
        disk.execute(oldStore::close); disk.shutdown();
        loaded = false; reload = false;
        super.onDestroyView();
    }
}
