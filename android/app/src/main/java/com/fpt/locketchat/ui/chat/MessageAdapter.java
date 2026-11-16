package com.fpt.locketchat.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.media.MediaPlayer;
import android.widget.LinearLayout;
import com.fpt.locketchat.utils.MessageFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import com.bumptech.glide.Glide;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Message;
import com.fpt.locketchat.utils.FileUtils;
import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final Context context;
    private final int currentUserId;
    private List<Message> messages = new ArrayList<>();
    private MediaPlayer player;
    private TextView playingLabel;
    private int playingId = -1;
    private android.app.AlertDialog videoDialog;
    private android.widget.VideoView activeVideo;

    public void releasePlayer() {
        if (player != null) { player.release(); player = null; }
        if (playingLabel != null) playingLabel.setText("▶ Phát tin nhắn thoại");
        playingLabel = null;
        playingId = -1;
        if (activeVideo != null) { activeVideo.stopPlayback(); activeVideo = null; }
        if (videoDialog != null) { videoDialog.dismiss(); videoDialog = null; }
    }

    private void metadata(View view) {
        TextView time = new TextView(context);
        time.setTag("message_metadata");
        time.setTextSize(10);
        time.setTextColor(android.graphics.Color.GRAY);
        ((LinearLayout) view).addView(time);
    }

    public MessageAdapter(Context context, int currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
        setHasStableIds(true);
    }

    public void setMessages(List<Message> newMessages) {
        List<Message> before = messages;
        List<Message> after = newMessages == null ? new ArrayList<>() : new ArrayList<>(newMessages);
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return after.size(); }
            public boolean areItemsTheSame(int oldIndex, int newIndex) {
                return before.get(oldIndex).getId() == after.get(newIndex).getId();
            }
            public boolean areContentsTheSame(int oldIndex, int newIndex) {
                Message old = before.get(oldIndex), next = after.get(newIndex);
                return java.util.Objects.equals(old.getContent(), next.getContent())
                        && java.util.Objects.equals(old.getFileUrl(), next.getFileUrl())
                        && java.util.Objects.equals(old.getMessageType(), next.getMessageType())
                        && java.util.Objects.equals(old.getSenderName(), next.getSenderName())
                        && java.util.Objects.equals(old.getCreatedAt(), next.getCreatedAt());
            }
        });
        messages = after;
        changes.dispatchUpdatesTo(this);
    }
    @Override public long getItemId(int position) { return messages.get(position).getId(); }

    @Override
    public int getItemViewType(int position) {
        Message msg = messages.get(position);
        return msg.getSenderId() == currentUserId ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false);
            metadata(view);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false);
            metadata(view);
            return new ReceivedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        TextView metadata = holder.itemView.findViewWithTag("message_metadata");
        metadata.setText((msg.getSenderId() != currentUserId && msg.getSenderName() != null ? msg.getSenderName() + " • " : "")
                + MessageFormat.time(msg.getCreatedAt()));
        String serverHost = ApiClient.BASE_URL.replace("/api/", "");

        if (holder instanceof SentViewHolder) {
            SentViewHolder sentHolder = (SentViewHolder) holder;
            bindMessageData(
                    msg, serverHost,
                    sentHolder.tvSent, sentHolder.cardImage, sentHolder.ivImage,
                    sentHolder.layoutFile, sentHolder.tvFileName, sentHolder.tvFileSize,
                    sentHolder.layoutMomentReply, sentHolder.ivMomentImage, sentHolder.tvMomentComment,
                    sentHolder.tvSentMomentBadge
            );
        } else if (holder instanceof ReceivedViewHolder) {
            ReceivedViewHolder recHolder = (ReceivedViewHolder) holder;
            bindMessageData(
                    msg, serverHost,
                    recHolder.tvReceived, recHolder.cardImage, recHolder.ivImage,
                    recHolder.layoutFile, recHolder.tvFileName, recHolder.tvFileSize,
                    recHolder.layoutMomentReply, recHolder.ivMomentImage, recHolder.tvMomentComment,
                    recHolder.tvReceivedMomentBadge
            );
        }
    }

    private void bindMessageData(Message msg, String serverHost,
                                TextView tvText, CardView cardImage, ImageView ivImage,
                                View layoutFile, TextView tvFileName, TextView tvFileSize,
                                View layoutMomentReply, ImageView ivMomentImage, TextView tvMomentComment,
                                TextView tvMomentBadge) {
        String type = msg.getMessageType();

        // 1. Tin nhắn dạng Bình luận / Phản hồi Khoảnh khắc Locket (Chuẩn Ảnh 1)
        if ("MOMENT".equalsIgnoreCase(type)) {
            tvText.setVisibility(View.GONE);
            cardImage.setVisibility(View.GONE);
            layoutFile.setVisibility(View.GONE);
            layoutMomentReply.setVisibility(View.VISIBLE);

            String fullUrl = msg.getFileUrl();
            if (fullUrl != null && !fullUrl.startsWith("http")) {
                fullUrl = serverHost + fullUrl;
            }

            Glide.with(context)
                    .load(fullUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(ivMomentImage);

            if (tvMomentBadge != null) {
                String badgeText = "You";
                String fileMeta = msg.getFileName();
                if (fileMeta != null && fileMeta.contains("|")) {
                    String[] parts = fileMeta.split("\\|", 2);
                    try {
                        int ownerId = Integer.parseInt(parts[0]);
                        String ownerName = parts.length > 1 ? parts[1] : "Bạn bè";
                        badgeText = (ownerId == currentUserId) ? "You" : ownerName;
                    } catch (Exception e) {
                        badgeText = parts.length > 1 ? parts[1] : "You";
                    }
                } else if (fileMeta != null && !fileMeta.trim().isEmpty()) {
                    badgeText = fileMeta;
                } else {
                    badgeText = (msg.getSenderId() == currentUserId) ? "Bạn bè" : "You";
                }
                tvMomentBadge.setText(badgeText);
            }

            String comment = msg.getContent();
            if (comment != null && !comment.trim().isEmpty()) {
                tvMomentComment.setVisibility(View.VISIBLE);
                tvMomentComment.setText(comment);
            } else {
                tvMomentComment.setVisibility(View.GONE);
            }
        }
        // 2. Tin nhắn Text thông thường
        else if ("TEXT".equalsIgnoreCase(type) || msg.getFileUrl() == null) {
            tvText.setVisibility(View.VISIBLE);
            tvText.setText(msg.getContent());
            cardImage.setVisibility(View.GONE);
            layoutFile.setVisibility(View.GONE);
            layoutMomentReply.setVisibility(View.GONE);
        }
        // 3. Tin nhắn Hình ảnh
        else if ("IMAGE".equalsIgnoreCase(type)) {
            tvText.setVisibility(View.GONE);
            layoutFile.setVisibility(View.GONE);
            layoutMomentReply.setVisibility(View.GONE);
            cardImage.setVisibility(View.VISIBLE);

            String fullUrl = ApiClient.resolveUrl(msg.getFileUrl());
            Glide.with(context)
                    .load(fullUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(ivImage);
        }
        // 4. Tin nhắn File tài liệu
        else {
            tvText.setVisibility(View.GONE);
            cardImage.setVisibility(View.GONE);
            layoutMomentReply.setVisibility(View.GONE);
            layoutFile.setVisibility(View.VISIBLE);

            boolean audio = "AUDIO".equalsIgnoreCase(type);
            boolean video = "VIDEO".equalsIgnoreCase(type);
            tvFileName.setText(audio ? (playingId == msg.getId() ? "■ Dừng tin nhắn thoại" : "▶ Phát tin nhắn thoại")
                    : (video ? "▶ Video • " : "📎 ") + (msg.getFileName() != null ? msg.getFileName() : "File đính kèm"));
            if (audio && playingId == msg.getId()) playingLabel = tvFileName;
            long size = msg.getFileSize() != null ? msg.getFileSize() : 0;
            tvFileSize.setText(FileUtils.formatFileSize(size));

            // Bấm vào file để mở link tải
            layoutFile.setOnClickListener(v -> {
                String fullUrl = ApiClient.resolveUrl(msg.getFileUrl());
                if (audio) {
                    boolean stopping = playingId == msg.getId();
                    releasePlayer();
                    if (stopping) return;
                    MediaPlayer next = new MediaPlayer();
                    player = next; playingId = msg.getId(); playingLabel = tvFileName;
                    tvFileName.setText("Đang tải âm thanh…");
                    try {
                        next.setDataSource(fullUrl);
                        next.setOnPreparedListener(mp -> {
                            if (player == mp) { mp.start(); tvFileName.setText("■ Dừng tin nhắn thoại"); }
                        });
                        next.setOnCompletionListener(mp -> releasePlayer());
                        next.setOnErrorListener((mp, what, extra) -> { releasePlayer();
                            android.widget.Toast.makeText(context, "Không phát được âm thanh; kiểm tra mạng", android.widget.Toast.LENGTH_SHORT).show(); return true; });
                        next.prepareAsync();
                    } catch (Exception e) { releasePlayer(); }
                    return;
                }
                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                if (video) {
                    releasePlayer();
                    android.widget.VideoView videoView = new android.widget.VideoView(context);
                    android.widget.FrameLayout container = new android.widget.FrameLayout(context);
                    container.addView(videoView, new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * context.getResources().getDisplayMetrics().density), android.view.Gravity.CENTER));
                    android.widget.MediaController controls = new android.widget.MediaController(context);
                    controls.setAnchorView(videoView);
                    videoView.setMediaController(controls);
                    android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context).setTitle("Video")
                            .setView(container).setNegativeButton("Đóng", null).create();
                    activeVideo = videoView;
                    videoDialog = dialog;
                    dialog.setOnDismissListener(d -> { videoView.stopPlayback(); activeVideo = null; videoDialog = null; });
                    videoView.setOnErrorListener((mp, what, extra) -> {
                        android.widget.Toast.makeText(context, "Không phát được video; kiểm tra mạng hoặc định dạng", android.widget.Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    dialog.show();
                    videoView.setVideoURI(Uri.parse(fullUrl));
                    videoView.setOnPreparedListener(mp -> videoView.start());
                    return;
                }
                browserIntent.setData(Uri.parse(fullUrl));
                try { context.startActivity(browserIntent); }
                catch (android.content.ActivityNotFoundException e) {
                    android.widget.Toast.makeText(context, "Chưa có ứng dụng mở loại tệp này", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if ((holder instanceof SentViewHolder && ((SentViewHolder) holder).tvFileName == playingLabel)
                || (holder instanceof ReceivedViewHolder && ((ReceivedViewHolder) holder).tvFileName == playingLabel)) {
            releasePlayer();
        }
        super.onViewRecycled(holder);
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView tvSent, tvFileName, tvFileSize;
        CardView cardImage;
        ImageView ivImage;
        View layoutFile;

        // Moment Reply Views
        View layoutMomentReply;
        ImageView ivMomentImage;
        TextView tvMomentComment;
        TextView tvSentMomentBadge;

        public SentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSent = itemView.findViewById(R.id.tvSentMessage);
            cardImage = itemView.findViewById(R.id.cardSentImage);
            ivImage = itemView.findViewById(R.id.ivSentImage);
            layoutFile = itemView.findViewById(R.id.layoutSentFile);
            tvFileName = itemView.findViewById(R.id.tvSentFileName);
            tvFileSize = itemView.findViewById(R.id.tvSentFileSize);

            layoutMomentReply = itemView.findViewById(R.id.layoutSentMomentReply);
            ivMomentImage = itemView.findViewById(R.id.ivSentMomentImage);
            tvMomentComment = itemView.findViewById(R.id.tvSentMomentComment);
            tvSentMomentBadge = itemView.findViewById(R.id.tvSentMomentBadge);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView tvReceived, tvFileName, tvFileSize;
        CardView cardImage;
        ImageView ivImage;
        View layoutFile;

        // Moment Reply Views
        View layoutMomentReply;
        ImageView ivMomentImage;
        TextView tvMomentComment;
        TextView tvReceivedMomentBadge;

        public ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvReceived = itemView.findViewById(R.id.tvReceivedMessage);
            cardImage = itemView.findViewById(R.id.cardReceivedImage);
            ivImage = itemView.findViewById(R.id.ivReceivedImage);
            layoutFile = itemView.findViewById(R.id.layoutReceivedFile);
            tvFileName = itemView.findViewById(R.id.tvReceivedFileName);
            tvFileSize = itemView.findViewById(R.id.tvReceivedFileSize);

            layoutMomentReply = itemView.findViewById(R.id.layoutReceivedMomentReply);
            ivMomentImage = itemView.findViewById(R.id.ivReceivedMomentImage);
            tvMomentComment = itemView.findViewById(R.id.tvReceivedMomentComment);
            tvReceivedMomentBadge = itemView.findViewById(R.id.tvReceivedMomentBadge);
        }
    }
}
