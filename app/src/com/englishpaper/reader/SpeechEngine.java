package com.englishpaper.reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Loads the on-demand Vosk engine pack (native libs + model) and runs offline recognition. */
public final class SpeechEngine {
    private static org.vosk.Model model;
    private static File libDir;

    private SpeechEngine() { }

    public static synchronized void load(File engineRoot) throws IOException {
        if (model != null) return;
        libDir = new File(engineRoot, "lib/arm64-v8a");
        File modelDir = new File(engineRoot, "model");
        if (!libDir.isDirectory() || !modelDir.isDirectory()) throw new IOException("引擎包不完整");
        System.setProperty("jna.boot.library.path", libDir.getAbsolutePath());
        System.load(new File(libDir, "libjnidispatch.so").getAbsolutePath());
        System.load(new File(libDir, "libvosk.so").getAbsolutePath());
        model = new org.vosk.Model(modelDir.getAbsolutePath());
    }

    public static synchronized boolean isLoaded() { return model != null; }

    /** Recognize a 16kHz mono WAV. grammarJson may be null for free recognition. Returns raw Vosk JSON. */
    public static synchronized String recognize(File wav, String grammarJson) throws IOException {
        if (model == null) throw new IOException("引擎未加载");
        byte[] all = Files.readAllBytes(wav.toPath());
        int off = dataOffset(all);
        org.vosk.Recognizer rec = new org.vosk.Recognizer(model, 16000f);
        try {
            if (grammarJson != null) rec.setGrammar(grammarJson);
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
