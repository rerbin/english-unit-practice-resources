package com.englishpaper.reader;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WrongBookDb {
    private final AppDatabase database;

    public WrongBookDb(AppDatabase database) { this.database = database; }
    private SQLiteDatabase getReadableDatabase() { return database.getReadableDatabase(); }
    private SQLiteDatabase getWritableDatabase() { return database.getWritableDatabase(); }

    public static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS mistakes (id INTEGER PRIMARY KEY AUTOINCREMENT,item_key TEXT NOT NULL UNIQUE,source_item_id TEXT,unit_id TEXT NOT NULL,unit_title TEXT,content_type TEXT,text_en TEXT NOT NULL,translation TEXT,pronunciation_error INTEGER NOT NULL DEFAULT 0 CHECK(pronunciation_error IN (0,1)),writing_error INTEGER NOT NULL DEFAULT 0 CHECK(writing_error IN (0,1)),stage TEXT NOT NULL DEFAULT 'active' CHECK(stage IN ('active','mastered')),mastered INTEGER NOT NULL DEFAULT 0 CHECK(mastered IN (0,1)),first_added_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,mastered_at INTEGER,archived_at INTEGER,last_correct_at INTEGER,last_restored_at INTEGER,attempts INTEGER NOT NULL DEFAULT 0,correct_count INTEGER NOT NULL DEFAULT 0,wrong_count INTEGER NOT NULL DEFAULT 0,review_count INTEGER NOT NULL DEFAULT 0,reactivated_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS mistake_words (mistake_id INTEGER NOT NULL REFERENCES mistakes(id) ON DELETE CASCADE,word_index INTEGER NOT NULL,word_text TEXT NOT NULL,PRIMARY KEY(mistake_id,word_index))");
        db.execSQL("CREATE TABLE IF NOT EXISTS learning_events (id INTEGER PRIMARY KEY AUTOINCREMENT,mistake_id INTEGER NOT NULL REFERENCES mistakes(id) ON DELETE CASCADE,event_type TEXT NOT NULL,event_at INTEGER NOT NULL,details TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mistakes_stage_updated ON mistakes(stage,updated_at DESC,id DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mistakes_errors ON mistakes(stage,pronunciation_error,writing_error)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_mistake_time ON learning_events(mistake_id,event_at DESC)");
    }

    private static long now() { return System.currentTimeMillis(); }

    private static void put(ContentValues v, String k, JSONObject o) throws org.json.JSONException { if (o.has(k) && !o.isNull(k)) v.put(k, o.getString(k)); }

    private void event(SQLiteDatabase db, long id, String type, String details) {
        ContentValues v = new ContentValues();
        v.put("mistake_id", id); v.put("event_type", type); v.put("event_at", now()); v.put("details", details);
        db.insert("learning_events", null, v);
    }

    public synchronized long saveMistake(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String key = o.getString("key");
            long id = -1; boolean existed = false; int oldReact = 0;
            try (Cursor c = db.rawQuery("SELECT id,stage,reactivated_count FROM mistakes WHERE item_key=?", new String[]{key})) {
                if (c.moveToFirst()) { id = c.getLong(0); existed = true; oldReact = c.getInt(2) + ("mastered".equals(c.getString(1)) ? 1 : 0); }
            }
            ContentValues v = new ContentValues();
            put(v, "source_item_id", o); put(v, "unit_id", o); put(v, "unit_title", o); put(v, "content_type", o);
            v.put("text_en", o.getString("text")); put(v, "translation", o);
            JSONArray types = o.getJSONArray("types");
            v.put("pronunciation_error", contains(types, "pronunciation") ? 1 : 0);
            v.put("writing_error", contains(types, "writing") ? 1 : 0);
            v.put("stage", "active"); v.put("mastered", 0); v.putNull("mastered_at"); v.putNull("archived_at"); v.put("updated_at", now()); v.put("reactivated_count", oldReact);
            if (existed) db.update("mistakes", v, "id=?", new String[]{String.valueOf(id)});
            else { v.put("item_key", key); v.put("first_added_at", now()); id = db.insertOrThrow("mistakes", null, v); }
            db.delete("mistake_words", "mistake_id=?", new String[]{String.valueOf(id)});
            JSONArray words = o.getJSONArray("words");
            for (int i = 0; i < words.length(); i++) {
                JSONObject w = words.getJSONObject(i);
                ContentValues x = new ContentValues();
                x.put("mistake_id", id); x.put("word_index", w.getInt("index")); x.put("word_text", w.getString("word"));
                db.insertOrThrow("mistake_words", null, x);
            }
            event(db, id, existed ? "mistake_updated" : "mistake_added", null);
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    private static boolean contains(JSONArray a, String s) throws org.json.JSONException { for (int i = 0; i < a.length(); i++) if (s.equals(a.getString(i))) return true; return false; }

    public synchronized String list(String stage, String filter, String unitId, int limit, int offset) throws Exception {
        if (limit < 1 || limit > 100) limit = 50;
        if (offset < 0) offset = 0;
        ArrayList<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("m.stage=?");
        args.add("mastered".equals(stage) ? "mastered" : "active");
        if ("pronunciation".equals(filter)) where.append(" AND m.pronunciation_error=1");
        else if ("writing".equals(filter)) where.append(" AND m.writing_error=1");
        if (unitId != null && !unitId.isEmpty()) { where.append(" AND m.unit_id=?"); args.add(unitId); }
        String sql = "SELECT m.*,ci.audio_key AS audio_key FROM mistakes m LEFT JOIN content_items ci ON ci.id=m.source_item_id WHERE " + where + " ORDER BY m.updated_at DESC,m.id DESC LIMIT ? OFFSET ?";
        args.add(String.valueOf(limit)); args.add(String.valueOf(offset));
        SQLiteDatabase db = getReadableDatabase();
        JSONArray out = new JSONArray();
        List<Long> ids = new ArrayList<>();
        Map<Long, JSONObject> rows = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                JSONObject o = row(c);
                long id = c.getLong(c.getColumnIndexOrThrow("id"));
                ids.add(id); rows.put(id, o);
                int akc = c.getColumnIndexOrThrow("audio_key");
                o.put("audioKey", c.isNull(akc) ? "" : c.getString(akc));
            }
        }
        if (!ids.isEmpty()) {
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) { if (i > 0) in.append(','); in.append(ids.get(i)); }
            try (Cursor w = db.rawQuery("SELECT mistake_id,word_index,word_text FROM mistake_words WHERE mistake_id IN (" + in + ") ORDER BY mistake_id,word_index", null)) {
                while (w.moveToNext()) {
                    JSONObject o = rows.get(w.getLong(0));
                    JSONArray words = o.optJSONArray("words");
                    if (words == null) { words = new JSONArray(); o.put("words", words); }
                    JSONObject z = new JSONObject();
                    z.put("index", w.getInt(1)); z.put("word", w.getString(2));
                    words.put(z);
                }
            }
        }
        for (JSONObject o : rows.values()) out.put(o);
        JSONObject result = new JSONObject();
        result.put("items", out); result.put("hasMore", out.length() == limit); result.put("offset", offset);
        return result.toString();
    }

    private JSONObject row(Cursor c) throws org.json.JSONException {
        JSONObject o = new JSONObject();
        o.put("id", c.getLong(c.getColumnIndexOrThrow("id")));
        o.put("key", c.getString(c.getColumnIndexOrThrow("item_key")));
        o.put("sourceItemId", c.getString(c.getColumnIndexOrThrow("source_item_id")));
        o.put("unitId", c.getString(c.getColumnIndexOrThrow("unit_id")));
        o.put("unitTitle", c.getString(c.getColumnIndexOrThrow("unit_title")));
        o.put("kind", c.getString(c.getColumnIndexOrThrow("content_type")));
        o.put("text", c.getString(c.getColumnIndexOrThrow("text_en")));
        o.put("translation", c.getString(c.getColumnIndexOrThrow("translation")));
        JSONArray t = new JSONArray();
        if (c.getInt(c.getColumnIndexOrThrow("pronunciation_error")) != 0) t.put("pronunciation");
        if (c.getInt(c.getColumnIndexOrThrow("writing_error")) != 0) t.put("writing");
        o.put("types", t);
        o.put("stage", c.getString(c.getColumnIndexOrThrow("stage")));
        o.put("mastered", c.getInt(c.getColumnIndexOrThrow("mastered")) != 0);
        o.put("attempts", c.getInt(c.getColumnIndexOrThrow("attempts")));
        o.put("correctCount", c.getInt(c.getColumnIndexOrThrow("correct_count")));
        o.put("wrongCount", c.getInt(c.getColumnIndexOrThrow("wrong_count")));
        return o;
    }

    public synchronized String counts(String unitId) throws Exception {
        SQLiteDatabase db = getReadableDatabase();
        boolean scoped = unitId != null && !unitId.isEmpty();
        String suffix = scoped ? " AND unit_id=?" : "";
        String[] args = scoped ? new String[]{unitId} : null;
        JSONObject o = new JSONObject();
        o.put("active", scalar(db, "SELECT count(*) FROM mistakes WHERE stage='active'" + suffix, args));
        o.put("mastered", scalar(db, "SELECT count(*) FROM mistakes WHERE stage='mastered'" + suffix, args));
        return o.toString();
    }

    private long scalar(SQLiteDatabase db, String sql, String[] args) { try (Cursor c = db.rawQuery(sql, args)) { return c.moveToFirst() ? c.getLong(0) : 0; } }

    public synchronized boolean toggleMaster(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int value = 0;
        try (Cursor c = db.rawQuery("SELECT mastered FROM mistakes WHERE id=? AND stage='active'", new String[]{String.valueOf(id)})) { if (c.moveToFirst()) value = c.getInt(0) == 0 ? 1 : 0; }
        ContentValues v = new ContentValues();
        v.put("mastered", value);
        if (value == 1) v.put("mastered_at", now()); else v.putNull("mastered_at");
        v.put("updated_at", now());
        db.update("mistakes", v, "id=? AND stage='active'", new String[]{String.valueOf(id)});
        event(db, id, value == 1 ? "marked_mastered" : "mastery_cancelled", null);
        return value == 1;
    }

    public synchronized boolean archive(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int review = 0;
        try (Cursor c = db.rawQuery("SELECT mastered,review_count FROM mistakes WHERE id=? AND stage='active'", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst() || c.getInt(0) == 0) return false;
            review = c.getInt(1);
        }
        ContentValues v = new ContentValues();
        v.put("stage", "mastered"); v.put("archived_at", now()); v.put("updated_at", now()); v.put("review_count", review + 1);
        db.update("mistakes", v, "id=?", new String[]{String.valueOf(id)});
        event(db, id, "moved_to_mastered", null);
        return true;
    }

    public synchronized void restore(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int react = 0;
        try (Cursor c = db.rawQuery("SELECT reactivated_count FROM mistakes WHERE id=?", new String[]{String.valueOf(id)})) { if (c.moveToFirst()) react = c.getInt(0); }
        ContentValues v = new ContentValues();
        v.put("stage", "active"); v.put("mastered", 0); v.putNull("mastered_at"); v.put("last_restored_at", now()); v.put("updated_at", now()); v.put("reactivated_count", react + 1);
        db.update("mistakes", v, "id=?", new String[]{String.valueOf(id)});
        event(db, id, "returned_to_practice", null);
    }

    public synchronized void spellResult(long id, boolean correct, String entered) {
        SQLiteDatabase db = getWritableDatabase();
        int pe = 0, we = 0;
        try (Cursor c = db.rawQuery("SELECT pronunciation_error,writing_error FROM mistakes WHERE id=?", new String[]{String.valueOf(id)})) { if (c.moveToFirst()) { pe = c.getInt(0); we = c.getInt(1); } }
        if (correct) {
            // 只有纯书写错误才允许拼写通过后自动掌握；含发音错误必须手工点“掌握”
            if (we == 1 && pe == 0) db.execSQL("UPDATE mistakes SET mastered=1,mastered_at=?,last_correct_at=?,updated_at=?,attempts=attempts+1,correct_count=correct_count+1 WHERE id=?", new Object[]{now(), now(), now(), id});
            else db.execSQL("UPDATE mistakes SET last_correct_at=?,updated_at=?,attempts=attempts+1,correct_count=correct_count+1 WHERE id=?", new Object[]{now(), now(), id});
        } else db.execSQL("UPDATE mistakes SET writing_error=1,updated_at=?,attempts=attempts+1,wrong_count=wrong_count+1 WHERE id=?", new Object[]{now(), id});
        event(db, id, correct ? "spelling_correct" : "spelling_wrong", entered);
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("mistakes", "id=?", new String[]{String.valueOf(id)});
    }
}
