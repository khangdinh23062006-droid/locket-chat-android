package com.fpt.locketchat.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.fpt.locketchat.R;
import com.fpt.locketchat.data.api.ApiClient;
import com.fpt.locketchat.ui.main.MainActivity;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * LocketWidgetProvider - Chức năng sáng tạo (5 điểm Rubric)
 * Hiển thị ảnh khoảnh khắc Locket mới nhất của bạn bè ra ngoài màn hình chính Android.
 */
public class LocketWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_locket_layout);

        // Khi người dùng bấm vào Widget trên màn hình chính -> Mở ứng dụng Locket Chat
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        // Gọi API lấy khoảnh khắc mới nhất để hiển thị ra Widget
        ApiClient.getService().getLatestWidgetMoment().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject json = response.body();
                    if (json.has("data") && !json.get("data").isJsonNull()) {
                        JsonObject data = json.getAsJsonObject("data");
                        String sender = data.has("sender_name") ? data.get("sender_name").getAsString() : "Locket";
                        String caption = data.has("caption") && !data.get("caption").isJsonNull() ? data.get("caption").getAsString() : "";

                        views.setTextViewText(R.id.widget_sender_name, sender);
                        views.setTextViewText(R.id.widget_caption, caption);
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // Giữ nguyên layout mặc định nếu mất mạng
                appWidgetManager.updateAppWidget(appWidgetId, views);
            }
        });

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
