package com.fpt.locketchat.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.data.model.Moment;
import java.util.ArrayList;
import java.util.List;

public class MomentFeedAdapter extends RecyclerView.Adapter<MomentFeedAdapter.MomentViewHolder> {

    private final Context context;
    private final List<Moment> moments = new ArrayList<>();

    public MomentFeedAdapter(Context context) {
        this.context = context;
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
    public MomentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment_card, parent, false);
        return new MomentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MomentViewHolder holder, int position) {
        Moment moment = moments.get(position);

        holder.tvSenderName.setText(moment.getSenderName());
        holder.tvMomentTime.setText(moment.getCreatedAt() != null ? moment.getCreatedAt() : "Vừa xong");

        String caption = moment.getCaption();
        if (caption != null && !caption.trim().isEmpty()) {
            holder.tvMomentCaption.setVisibility(View.VISIBLE);
            holder.tvMomentCaption.setText(caption);
        } else {
            holder.tvMomentCaption.setVisibility(View.GONE);
        }

        String serverHost = ApiClient.BASE_URL.replace("/api/", "");
        String fullUrl = moment.getImageUrl();
        if (fullUrl != null && !fullUrl.startsWith("http")) {
            fullUrl = serverHost + fullUrl;
        }

        Glide.with(context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(holder.ivMomentImage);
    }

    @Override
    public int getItemCount() {
        return moments.size();
    }

    static class MomentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMomentImage, ivSenderAvatar;
        TextView tvSenderName, tvMomentTime, tvMomentCaption;

        public MomentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMomentImage = itemView.findViewById(R.id.ivMomentImage);
            ivSenderAvatar = itemView.findViewById(R.id.ivSenderAvatar);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMomentTime = itemView.findViewById(R.id.tvMomentTime);
            tvMomentCaption = itemView.findViewById(R.id.tvMomentCaption);
        }
    }
}
