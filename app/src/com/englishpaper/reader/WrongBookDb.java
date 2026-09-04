package com.englishpaper.reader;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class WrongBookDb {
    private final AppDatabase database;
    public WrongBookDb(AppDatabase database) { this.database=database; }
    private SQLiteDatabase getReadableDatabase(){return database.getReadableDatabase();}
    private SQLiteDatabase getWritableDatabase(){return database.getWritableDatabase();}

    public static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS mistakes (id INTEGER PRIMARY KEY AUTOINCREMENT, item_key TEXT NOT NULL UNIQUE, source_item_id TEXT, unit_id TEXT, unit_title TEXT, content_type TEXT, text_en TEXT NOT NULL, translation TEXT, pronunciation_error INTEGER NOT NULL DEFAULT 0, writing_error INTEGER NOT NULL DEFAULT 0, stage TEXT NOT NULL DEFAULT 'active', mastered INTEGER NOT NULL DEFAULT 0, first_added_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, mastered_at INTEGER, archived_at INTEGER, last_correct_at INTEGER, last_restored_at INTEGER, attempts INTEGER NOT NULL DEFAULT 0, correct_count INTEGER NOT NULL DEFAULT 0, wrong_count INTEGER NOT NULL DEFAULT 0, review_count INTEGER NOT NULL DEFAULT 0, reactivated_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS mistake_words (mistake_id INTEGER NOT NULL, word_index INTEGER NOT NULL, word_text TEXT NOT NULL, normalized_word TEXT NOT NULL, PRIMARY KEY(mistake_id,word_index), FOREIGN KEY(mistake_id) REFERENCES mistakes(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS learning_events (id INTEGER PRIMARY KEY AUTOINCREMENT, mistake_id INTEGER NOT NULL, event_type TEXT NOT NULL, event_at INTEGER NOT NULL, details TEXT, FOREIGN KEY(mistake_id) REFERENCES mistakes(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mistakes_stage_updated ON mistakes(stage,updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mistakes_errors ON mistakes(stage,pronunciation_error,writing_error)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mistakes_unit ON mistakes(unit_id,stage)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_mistake_time ON learning_events(mistake_id,event_at DESC)");
    }

    private static long now(){ return System.currentTimeMillis(); }
    private static String norm(String s){ return s == null ? "" : s.toLowerCase(Locale.US).replace('’','\'').trim(); }
    private static void put(ContentValues v,String k,JSONObject o) throws JSONException { if(o.has(k)&&!o.isNull(k))v.put(k,o.getString(k)); }
    private void event(SQLiteDatabase db,long id,String type,String details){ ContentValues v=new ContentValues();v.put("mistake_id",id);v.put("event_type",type);v.put("event_at",now());v.put("details",details);db.insert("learning_events",null,v); }

    public synchronized long saveMistake(String json) throws Exception {
        JSONObject o=new JSONObject(json);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String key=o.getString("key");long id=-1;boolean existed=false;int oldReact=0;
            try(Cursor c=db.rawQuery("SELECT id,stage,reactivated_count FROM mistakes WHERE item_key=?",new String[]{key})){if(c.moveToFirst()){id=c.getLong(0);existed=true;oldReact=c.getInt(2)+("mastered".equals(c.getString(1))?1:0);}}
            ContentValues v=new ContentValues();put(v,"source_item_id",o);put(v,"unit_id",o);put(v,"unit_title",o);put(v,"content_type",o);v.put("text_en",o.getString("text"));put(v,"translation",o);JSONArray types=o.getJSONArray("types");v.put("pronunciation_error",contains(types,"pronunciation")?1:0);v.put("writing_error",contains(types,"writing")?1:0);v.put("stage","active");v.put("mastered",0);v.putNull("mastered_at");v.putNull("archived_at");v.put("updated_at",now());v.put("reactivated_count",oldReact);
            if(existed)db.update("mistakes",v,"id=?",new String[]{String.valueOf(id)});else{v.put("item_key",key);v.put("first_added_at",now());id=db.insertOrThrow("mistakes",null,v);}
            db.delete("mistake_words","mistake_id=?",new String[]{String.valueOf(id)});JSONArray words=o.getJSONArray("words");for(int i=0;i<words.length();i++){JSONObject w=words.getJSONObject(i);ContentValues x=new ContentValues();x.put("mistake_id",id);x.put("word_index",w.getInt("index"));x.put("word_text",w.getString("word"));x.put("normalized_word",norm(w.getString("word")));db.insertOrThrow("mistake_words",null,x);}
            event(db,id,existed?"mistake_updated":"mistake_added",null);db.setTransactionSuccessful();return id;
        } finally { db.endTransaction(); }
    }
    private static boolean contains(JSONArray a,String s)throws JSONException{for(int i=0;i<a.length();i++)if(s.equals(a.getString(i)))return true;return false;}

    public synchronized String list(String stage,String filter,int limit,int offset) throws Exception {
        if(limit<1||limit>100)limit=50;if(offset<0)offset=0;ArrayList<String> args=new ArrayList<>();StringBuilder where=new StringBuilder("stage=?");args.add("mastered".equals(stage)?"mastered":"active");if("pronunciation".equals(filter)){where.append(" AND pronunciation_error=1");}else if("writing".equals(filter)){where.append(" AND writing_error=1");}
        JSONArray out=new JSONArray();String sql="SELECT * FROM mistakes WHERE "+where+" ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?";args.add(String.valueOf(limit));args.add(String.valueOf(offset));SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){while(c.moveToNext()){JSONObject o=row(c);JSONArray words=new JSONArray();try(Cursor w=db.rawQuery("SELECT word_index,word_text FROM mistake_words WHERE mistake_id=? ORDER BY word_index",new String[]{String.valueOf(c.getLong(c.getColumnIndexOrThrow("id")))})){while(w.moveToNext()){JSONObject z=new JSONObject();z.put("index",w.getInt(0));z.put("word",w.getString(1));words.put(z);}}o.put("words",words);String ak="";String sid=o.optString("sourceItemId");if(sid!=null&&!sid.isEmpty()){try(Cursor a=db.rawQuery("SELECT audio_key FROM content_items WHERE id=?",new String[]{sid})){if(a.moveToFirst())ak=a.getString(0);}catch(Exception ignored){}}o.put("audioKey",ak==null?"":ak);out.put(o);}}
        JSONObject result=new JSONObject();result.put("items",out);result.put("hasMore",out.length()==limit);result.put("offset",offset);return result.toString();
    }
    private JSONObject row(Cursor c)throws JSONException{JSONObject o=new JSONObject();o.put("id",c.getLong(c.getColumnIndexOrThrow("id")));o.put("key",c.getString(c.getColumnIndexOrThrow("item_key")));o.put("sourceItemId",c.getString(c.getColumnIndexOrThrow("source_item_id")));o.put("unitId",c.getString(c.getColumnIndexOrThrow("unit_id")));o.put("unitTitle",c.getString(c.getColumnIndexOrThrow("unit_title")));o.put("kind",c.getString(c.getColumnIndexOrThrow("content_type")));o.put("text",c.getString(c.getColumnIndexOrThrow("text_en")));o.put("translation",c.getString(c.getColumnIndexOrThrow("translation")));JSONArray t=new JSONArray();if(c.getInt(c.getColumnIndexOrThrow("pronunciation_error"))!=0)t.put("pronunciation");if(c.getInt(c.getColumnIndexOrThrow("writing_error"))!=0)t.put("writing");o.put("types",t);o.put("stage",c.getString(c.getColumnIndexOrThrow("stage")));o.put("mastered",c.getInt(c.getColumnIndexOrThrow("mastered"))!=0);o.put("attempts",c.getInt(c.getColumnIndexOrThrow("attempts")));o.put("correctCount",c.getInt(c.getColumnIndexOrThrow("correct_count")));o.put("wrongCount",c.getInt(c.getColumnIndexOrThrow("wrong_count")));return o;}
    public synchronized String counts()throws Exception{SQLiteDatabase db=getReadableDatabase();JSONObject o=new JSONObject();o.put("active",scalar(db,"SELECT count(*) FROM mistakes WHERE stage='active'"));o.put("mastered",scalar(db,"SELECT count(*) FROM mistakes WHERE stage='mastered'"));return o.toString();}
    private long scalar(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    public synchronized void toggleMaster(long id){SQLiteDatabase db=getWritableDatabase();int value=0;try(Cursor c=db.rawQuery("SELECT mastered FROM mistakes WHERE id=? AND stage='active'",new String[]{String.valueOf(id)})){if(c.moveToFirst())value=c.getInt(0)==0?1:0;}ContentValues v=new ContentValues();v.put("mastered",value);if(value==1)v.put("mastered_at",now());else v.putNull("mastered_at");v.put("updated_at",now());db.update("mistakes",v,"id=? AND stage='active'",new String[]{String.valueOf(id)});event(db,id,value==1?"marked_mastered":"mastery_cancelled",null);}
    public synchronized boolean archive(long id){SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT mastered FROM mistakes WHERE id=? AND stage='active'",new String[]{String.valueOf(id)})){if(!c.moveToFirst()||c.getInt(0)==0)return false;}ContentValues v=new ContentValues();v.put("stage","mastered");v.put("archived_at",now());v.put("updated_at",now());db.execSQL("UPDATE mistakes SET stage='mastered',archived_at=?,updated_at=?,review_count=review_count+1 WHERE id=?",new Object[]{now(),now(),id});event(db,id,"moved_to_mastered",null);return true;}
    public synchronized void delete(long id){SQLiteDatabase db=getWritableDatabase();db.delete("mistakes","id=?",new String[]{String.valueOf(id)});}
    public synchronized void restore(long id){SQLiteDatabase db=getWritableDatabase();db.execSQL("UPDATE mistakes SET stage='active',mastered=0,mastered_at=NULL,last_restored_at=?,updated_at=?,reactivated_count=reactivated_count+1 WHERE id=?",new Object[]{now(),now(),id});event(db,id,"returned_to_practice",null);}
    public synchronized void spellResult(long id,boolean correct,String entered){SQLiteDatabase db=getWritableDatabase();if(correct)db.execSQL("UPDATE mistakes SET mastered=1,mastered_at=?,last_correct_at=?,updated_at=?,attempts=attempts+1,correct_count=correct_count+1 WHERE id=?",new Object[]{now(),now(),now(),id});else db.execSQL("UPDATE mistakes SET writing_error=1,updated_at=?,attempts=attempts+1,wrong_count=wrong_count+1 WHERE id=?",new Object[]{now(),id});event(db,id,correct?"spelling_correct":"spelling_wrong",entered);}

}
