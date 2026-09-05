package com.englishpaper.reader;

import android.content.Context;
import java.io.*;

/**
 * Owns non-database files.
 * Layout: files/learning_content/packs/<unitId>/{manifest.json,audio/*};
 * cache/import_staging holds only transient extract/restore data and is wiped on boot.
 */
public final class PrivateFileStore {
    private final File root, staging;

    public PrivateFileStore(Context context) {
        root = new File(context.getFilesDir(), "learning_content");
        staging = new File(context.getCacheDir(), "import_staging");
        for (File f : new File[]{root, packsRoot(), staging}) if (!f.exists() && !f.mkdirs()) throw new IllegalStateException("无法创建私有目录");
        cleanupStaging();
    }

    public File contentRoot() { return root; }
    public File packsRoot() { return new File(root, "packs"); }
    public File importStagingRoot() { return staging; }
    public void cleanupStaging() { deleteChildren(staging); }
    private static void deleteChildren(File d) { File[] fs = d.listFiles(); if (fs == null) return; for (File f : fs) { if (f.isDirectory()) deleteChildren(f); f.delete(); } }
}
