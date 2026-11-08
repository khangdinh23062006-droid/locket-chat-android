package com.fpt.locketchat.ui.main;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.model.Conversation;
import com.fpt.locketchat.ui.chat.ChatActivity;
import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private final Context context;
    private List<Conversation> list = new ArrayList<>();

    public ConversationAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Conversation> newList) {
        this.list = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conv = list.get(position);
        holder.tvName.setText(conv.getDisplayName());
        holder.tvLastMessage.setText(conv.getLastMessage());

        // Bấm vào item -> Mở phòng Chat trực tiếp
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("conversation_id", conv.getId());
            intent.putExtra("display_name", conv.getDisplayName());
            context.startActivity(intent);
        });

        // Nút Gọi Điện Nhanh (Chức năng sáng tạo Rubric)
        if (conv.getTargetPhone() != null && !conv.getTargetPhone().isEmpty()) {
            holder.btnQuickCall.setVisibility(View.VISIBLE);
            holder.btnQuickCall.setOnClickListener(v -> {
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse("tel:" + conv.getTargetPhone()));
                context.startActivity(dialIntent);
            });
        } else {
            holder.btnQuickCall.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvLastMessage;
        Button btnQuickCall;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            btnQuickCall = itemView.findViewById(R.id.btnQuickCall);
        }
    }
}
