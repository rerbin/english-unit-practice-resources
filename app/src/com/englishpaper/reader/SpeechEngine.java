package com.englishpaper.reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Loads the on-demand Vosk engine pack (native libs + model) and runs offline recognition. Never throws: failures surface via loadError. */
public final class SpeechEngine {
    private static org.vosk.Model model;
    private static File libDir;
    private static String loadError;

    private SpeechEngine() { }

    public static synchronized String getLoadError() { return loadError; }

    public static synchronized boolean isLoaded() { return model != null; }

    /** Pick the first ABI directory present in the pack that this device supports. */
    public static File abiLibDir(File engineRoot) {
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            File d = new File(engineRoot, "lib/" + abi);
            if (new File(d, "libvosk.so").isFile()) return d;
        }
        return null;
    }

    public static synchronized boolean load(File engineRoot) {
        if (model != null) return true;
        loadError = null;
        try {
            libDir = abiLibDir(engineRoot);
            if (libDir == null) { loadError = "跟读引擎与本机 CPU 架构不兼容。"; return false; }
            File modelDir = new File(engineRoot, "model");
            if (!modelDir.isDirectory()) { loadError = "跟读引擎包不完整。"; return false; }
            System.setProperty("jna.boot.library.path", libDir.getAbsolutePath());
            System.load(new File(libDir, "libjnidispatch.so").getAbsolutePath());
            System.load(new File(libDir, "libvosk.so").getAbsolutePath());
            model = new org.vosk.Model(modelDir.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            android.util.Log.e("SpeechEngine", "Unable to load speech engine", t);
            loadError = "跟读引擎加载失败：" + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : "（" + t.getMessage() + "）");
            model = null;
            return false;
        }
    }

    /** Recognize a 16kHz mono WAV. grammarJson may be null for free recognition. Returns raw Vosk JSON or null on failure. */
    public static synchronized String recognize(File wav, String grammarJson) {
        if (model == null) return null;
        try {
            byte[] all = Files.readAllBytes(wav.toPath());
            int off = dataOffset(all);
            org.vosk.Recognizer rec = grammarJson == null ? new org.vosk.Recognizer(model, 16000f) : new org.vosk.Recognizer(model, 16000f, grammarJson);
            try {
                rec.setWords(true);
                byte[] chunk = new byte[3200];
                int pos = off;
                boolean ended = false;
                while (pos < all.length) {
                    int n = Math.min(chunk.length, all.length - pos);
                    System.arraycopy(all, pos, chunk, 0, n);
                    pos += n;
                    if (rec.acceptWaveForm(chunk, n)) { ended = true; break; }
                }
                return ended ? rec.getResult() : rec.getFinalResult();
            } finally {
                try { rec.close(); } catch (Exception ignored) { }
            }
        } catch (Throwable t) {
            android.util.Log.e("SpeechEngine", "Recognition failed", t);
            loadError = "语音对比失败：" + t.getClass().getSimpleName();
            return null;
        }
    }

    private static int dataOffset(byte[] all) {
        for (int i = 12; i + 8 < all.length; i++) {
            if (all[i] == 'd' && all[i + 1] == 'a' && all[i + 2] == 't' && all[i + 3] == 'a') {
                int len = (all[i + 4] & 0xff) | (all[i + 5] & 0xff) << 8 | (all[i + 6] & 0xff) << 16 | (all[i + 7] & 0xff) << 24;
                if (len > 0 && i + 8 + len <= all.length) return i + 8;
            }
        }
        return 44;
    }
}
