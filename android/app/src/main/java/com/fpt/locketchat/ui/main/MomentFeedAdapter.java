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
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Moment;
import java.util.ArrayList;
import java.util.List;

public class MomentFeedAdapter extends RecyclerView.Adapter<MomentFeedAdapter.MomentPageViewHolder> {

    public interface OnMomentActionListener {
        void onSendComment(Moment moment, String comment);
        void onSendReaction(Moment moment, String reactionEmoji);
    }

    private final Context context;
    private final List<Moment> moments = new ArrayList<>();
    private OnMomentActionListener listener;

    public MomentFeedAdapter(Context context) {
        this.context = context;
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

        // Hiển thị tên người gửi và thời gian
        String senderName = moment.getSenderName();
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

        // Tải ảnh khoảnh khắc
        String serverHost = ApiClient.BASE_URL.replace("/api/", "");
        String fullUrl = moment.getImageUrl();
        if (fullUrl != null && !fullUrl.startsWith("http")) {
            fullUrl = serverHost + fullUrl;
        }

        Glide.with(context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(holder.ivMomentPhoto);

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

        // Bấm các icon cảm xúc nhanh
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
        ImageView ivMomentPhoto;
        TextView tvMomentCaptionOverlay, tvSenderAndDate;
        EditText etMomentComment;
        ImageButton btnSendComment;
        LinearLayout layoutReactions;
        TextView btnReactHeart, btnReactFire, btnReactLaugh, btnReactYellowHeart;

        public MomentPageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMomentPhoto = itemView.findViewById(R.id.ivMomentPhoto);
            tvMomentCaptionOverlay = itemView.findViewById(R.id.tvMomentCaptionOverlay);
            tvSenderAndDate = itemView.findViewById(R.id.tvSenderAndDate);
            etMomentComment = itemView.findViewById(R.id.etMomentComment);
            btnSendComment = itemView.findViewById(R.id.btnSendComment);
            layoutReactions = itemView.findViewById(R.id.layoutReactions);
            btnReactHeart = itemView.findViewById(R.id.btnReactHeart);
            btnReactFire = itemView.findViewById(R.id.btnReactFire);
            btnReactLaugh = itemView.findViewById(R.id.btnReactLaugh);
            btnReactYellowHeart = itemView.findViewById(R.id.btnReactYellowHeart);
        }
    }
}
