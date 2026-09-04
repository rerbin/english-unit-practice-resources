package com.englishpaper.reader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** The single owner of the application's SQLite connection and schema. */
public final class AppDatabase extends SQLiteOpenHelper {
    private static final String NAME = "english_unit_practice_v2.db";
    private static final int VERSION = 4;
    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) instance = new AppDatabase(context.getApplicationContext());
            }
        }
        return instance;
    }

    private AppDatabase(Context context) { super(context, NAME, null, VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
        db.enableWriteAheadLogging();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        ContentDb.create(db);
        WrongBookDb.create(db);
    }

    /** Development policy: schema changes intentionally recreate an empty database. */
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        dropAll(db);
        onCreate(db);
    }

    @Override public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        dropAll(db);
        onCreate(db);
    }

    private static void dropAll(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=OFF");
        for (String table : new String[]{"learning_events","mistake_words","mistakes","item_options","content_items","sections","package_imports","audio_assets","units","textbooks","app_state"}) {
            db.execSQL("DROP TABLE IF EXISTS " + table);
        }
        db.execSQL("PRAGMA foreign_keys=ON");
    }
}
