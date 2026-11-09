package com.fpt.locketchat.ui.main;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Moment;
import java.util.ArrayList;
import java.util.List;

public class MomentFeedAdapter extends RecyclerView.Adapter<MomentFeedAdapter.MomentPageViewHolder> {

    public interface OnMomentActionListener {
        void onSendComment(Moment moment, String comment);
        void onSendReaction(Moment moment, String reactionEmoji);
        void onOpenActivity(Moment moment);
    }

    private final Context context;
    private final int currentUserId;
    private final List<Moment> moments = new ArrayList<>();
    private OnMomentActionListener listener;

    public MomentFeedAdapter(Context context) {
        this(context, -1);
    }

    public MomentFeedAdapter(Context context, int currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
    }

    public void setListener(OnMomentActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<Moment> newMoments) {
        moments.clear();
        if (newMoments != null) {
            moments.addAll(newMoments);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MomentPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment_page, parent, false);
        return new MomentPageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MomentPageViewHolder holder, int position) {
        Moment moment = moments.get(position);

        // Hiển thị tên người gửi (Nếu là của tôi thì hiển thị "You") và thời gian
        String senderName = (moment.getSenderId() == currentUserId) ? "You" : moment.getSenderName();
        String timeAgo = formatTimeAgo(moment.getCreatedAt());
        holder.tvSenderAndDate.setText(senderName + "  •  " + timeAgo);

        // Hiển thị caption nếu có
        String caption = moment.getCaption();
        if (caption != null && !caption.trim().isEmpty()) {
            holder.tvMomentCaptionOverlay.setVisibility(View.VISIBLE);
            holder.tvMomentCaptionOverlay.setText(caption);
        } else {
            holder.tvMomentCaptionOverlay.setVisibility(View.GONE);
        }

        // Tải ảnh khoảnh khắc bo tròn 4 góc 32dp
        String serverHost = ApiClient.BASE_URL.replace("/api/", "");
        String fullUrl = moment.getImageUrl();
        if (fullUrl != null && !fullUrl.startsWith("http")) {
            fullUrl = serverHost + fullUrl;
        }

        int radiusPx = (int) (32 * context.getResources().getDisplayMetrics().density);
        Glide.with(context)
                .load(fullUrl)
                .transform(new CenterCrop(), new RoundedCorners(radiusPx))
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(holder.ivMomentPhoto);

        // Kiểm tra quyền sở hữu khoảnh khắc
        boolean isMine = (moment.getSenderId() == currentUserId);

        if (isMine) {
            // Ảnh của tôi: ẩn thanh bình luận, hiển thị Viên thuốc Hoạt động (Chuẩn Ảnh 3 & 4)
            holder.layoutCommentBar.setVisibility(View.GONE);
            holder.layoutActivityPill.setVisibility(View.VISIBLE);

            List<Moment.Reaction> reactions = moment.getReactions();
            if (reactions.isEmpty()) {
                holder.tvActivitySummary.setVisibility(View.GONE);
            } else {
                holder.tvActivitySummary.setVisibility(View.VISIBLE);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(reactions.size(), 4); i++) {
                    sb.append(reactions.get(i).getEmoji());
                }
                if (reactions.size() > 4) {
                    sb.append(" +").append(reactions.size() - 4);
                }
                holder.tvActivitySummary.setText(sb.toString());
            }

            holder.layoutActivityPill.setOnClickListener(v -> {
                if (listener != null) listener.onOpenActivity(moment);
            });
        } else {
            // Ảnh của bạn bè: hiển thị thanh bình luận & icon react
            holder.layoutCommentBar.setVisibility(View.VISIBLE);
            holder.layoutActivityPill.setVisibility(View.GONE);

            // Xử lý khi nhập tin nhắn bình luận
            holder.etMomentComment.setText("");
            holder.btnSendComment.setVisibility(View.GONE);
            holder.layoutReactions.setVisibility(View.VISIBLE);

            holder.etMomentComment.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean hasText = s != null && s.toString().trim().length() > 0;
                    holder.btnSendComment.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    holder.layoutReactions.setVisibility(hasText ? View.GONE : View.VISIBLE);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Bấm gửi bình luận
            holder.btnSendComment.setOnClickListener(v -> {
                String text = holder.etMomentComment.getText().toString().trim();
                if (!text.isEmpty() && listener != null) {
                    listener.onSendComment(moment, text);
                    holder.etMomentComment.setText("");
                }
            });

            holder.etMomentComment.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    String text = holder.etMomentComment.getText().toString().trim();
                    if (!text.isEmpty() && listener != null) {
                        listener.onSendComment(moment, text);
                        holder.etMomentComment.setText("");
                    }
                    return true;
                }
                return false;
            });

            // Bấm các icon cảm xúc nhanh (Thả react vào khoảnh khắc)
            holder.btnReactHeart.setOnClickListener(v -> {
                if (listener != null) listener.onSendReaction(moment, "❤️");
            });
            holder.btnReactFire.setOnClickListener(v -> {
                if (listener != null) listener.onSendReaction(moment, "🔥");
            });
            holder.btnReactLaugh.setOnClickListener(v -> {
                if (listener != null) listener.onSendReaction(moment, "😂");
            });
            holder.btnReactYellowHeart.setOnClickListener(v -> {
                if (listener != null) listener.onSendReaction(moment, "💛");
            });
        }
    }

    private String formatTimeAgo(String dateStr) {
        if (dateStr == null) return "Vừa xong";
        if (dateStr.length() >= 16) {
            try {
                // Rút ngắn định dạng hiển thị
                return dateStr.substring(11, 16);
            } catch (Exception ignored) {}
        }
        return "Vừa xong";
    }

    @Override
    public int getItemCount() {
        return moments.size();
    }

    static class MomentPageViewHolder extends RecyclerView.ViewHolder {
        CardView cardMomentFrame;
        ImageView ivMomentPhoto;
        TextView tvMomentCaptionOverlay, tvSenderAndDate;
        LinearLayout layoutCommentBar;
        EditText etMomentComment;
        ImageButton btnSendComment;
        LinearLayout layoutReactions;
        TextView btnReactHeart, btnReactFire, btnReactLaugh, btnReactYellowHeart;
        LinearLayout layoutActivityPill;
        TextView tvActivitySummary;

        public MomentPageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardMomentFrame = itemView.findViewById(R.id.cardMomentFrame);
            ivMomentPhoto = itemView.findViewById(R.id.ivMomentPhoto);
            tvMomentCaptionOverlay = itemView.findViewById(R.id.tvMomentCaptionOverlay);
            tvSenderAndDate = itemView.findViewById(R.id.tvSenderAndDate);
            layoutCommentBar = itemView.findViewById(R.id.layoutCommentBar);
            etMomentComment = itemView.findViewById(R.id.etMomentComment);
            btnSendComment = itemView.findViewById(R.id.btnSendComment);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
            btnReactHeart = itemView.findViewById(R.id.btnReactHeart);
            btnReactFire = itemView.findViewById(R.id.btnReactFire);
            btnReactLaugh = itemView.findViewById(R.id.btnReactLaugh);
            btnReactYellowHeart = itemView.findViewById(R.id.btnReactYellowHeart);
            layoutActivityPill = itemView.findViewById(R.id.layoutActivityPill);
            tvActivitySummary = itemView.findViewById(R.id.tvActivitySummary);
        }
    }
}
