package com.fpt.locketchat.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.fpt.locketchat.data.model.Message;
import com.fpt.locketchat.data.model.Conversation;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/** One database per account. All calls run on the repository's disk executor. */
public final class ChatStore extends SQLiteOpenHelper {
    private final Gson gson = new Gson();
    public ChatStore(Context context, int userId) {
        super(context.getApplicationContext(), "chat_" + userId + ".db", null, 1);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY, conversation_id INTEGER NOT NULL, json TEXT NOT NULL)");
        db.execSQL("CREATE INDEX message_conversation ON messages(conversation_id, id)");
        db.execSQL("CREATE TABLE conversations (id INTEGER PRIMARY KEY, position INTEGER, json TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    public List<Message> messages(int conversationId) {
        List<Message> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT json FROM messages WHERE conversation_id=? ORDER BY id", new String[]{String.valueOf(conversationId)})) {
            while (c.moveToNext()) result.add(gson.fromJson(c.getString(0), Message.class));
        }
        return result;
    }
    public void saveMessages(List<Message> messages) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Message message : messages) {
                ContentValues row = new ContentValues();
                row.put("id", message.getId());
                row.put("conversation_id", message.getConversationId());
                row.put("json", gson.toJson(message));
                db.insertWithOnConflict("messages", null, row, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public List<Conversation> conversations() {
        List<Conversation> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM conversations ORDER BY position", null)) {
            while (c.moveToNext()) result.add(gson.fromJson(c.getString(0), Conversation.class));
        }
        return result;
    }
    public void saveConversations(List<Conversation> conversations) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("conversations", null, null);
            int position = 0;
            for (Conversation conversation : conversations) {
                ContentValues row = new ContentValues();
                row.put("id", conversation.getId());
                row.put("position", position++);
                row.put("json", gson.toJson(conversation));
                db.insertOrThrow("conversations", null, row);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
