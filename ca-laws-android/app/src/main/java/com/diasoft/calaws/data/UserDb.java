package com.diasoft.calaws.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The user's own data (bookmarks, reading history); kept apart from the replaceable law database. */
public class UserDb extends SQLiteOpenHelper {

    public static class Entry {
        public long id;
        public String docId;
        public int seq = -1;
        public String title;
        public String label;
        public String note;
        public long time;
    }

    public UserDb(Context context) {
        super(context.getApplicationContext(), "user.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id TEXT NOT NULL, " +
                "seq INTEGER NOT NULL, title TEXT, label TEXT, note TEXT, created INTEGER, UNIQUE(doc_id, seq))");
        db.execSQL("CREATE TABLE history (doc_id TEXT PRIMARY KEY, title TEXT, opened INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public Set<Integer> bookmarkedSeqs(String docId) {
        Set<Integer> out = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT seq FROM bookmarks WHERE doc_id = ?", new String[]{docId})) {
            while (c.moveToNext()) out.add(c.getInt(0));
        }
        return out;
    }

    /** Adds the bookmark if absent, removes it if present; returns true when it is now bookmarked. */
    public boolean toggleBookmark(String docId, int seq, String title, String label, String note) {
        SQLiteDatabase db = getWritableDatabase();
        int removed = db.delete("bookmarks", "doc_id = ? AND seq = ?", new String[]{docId, String.valueOf(seq)});
        if (removed > 0) return false;
        ContentValues v = new ContentValues();
        v.put("doc_id", docId);
        v.put("seq", seq);
        v.put("title", title);
        v.put("label", label);
        v.put("note", note);
        v.put("created", System.currentTimeMillis());
        db.insert("bookmarks", null, v);
        return true;
    }

    public List<Entry> bookmarks() {
        List<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, doc_id, seq, title, label, note, created FROM bookmarks ORDER BY created DESC", null)) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getLong(0);
                e.docId = c.getString(1);
                e.seq = c.getInt(2);
                e.title = c.getString(3);
                e.label = c.getString(4);
                e.note = c.getString(5);
                e.time = c.getLong(6);
                out.add(e);
            }
        }
        return out;
    }

    public void removeBookmark(long id) {
        getWritableDatabase().delete("bookmarks", "id = ?", new String[]{String.valueOf(id)});
    }

    public void addHistory(String docId, String title) {
        ContentValues v = new ContentValues();
        v.put("doc_id", docId);
        v.put("title", title);
        v.put("opened", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Entry> history() {
        List<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT doc_id, title, opened FROM history ORDER BY opened DESC LIMIT 200", null)) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.docId = c.getString(0);
                e.title = c.getString(1);
                e.time = c.getLong(2);
                out.add(e);
            }
        }
        return out;
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }
}
