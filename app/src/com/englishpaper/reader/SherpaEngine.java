package com.englishpaper.reader;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;

/** Whisper small.en (int8) recognition via sherpa-onnx. Native lib ships inside the APK. */
public final class SherpaEngine {
    private static boolean loaded;
    private static String loadError;

    private SherpaEngine() { }

    public static synchronized String getLoadError() { return loadError; }

    public static synchronized boolean ensureLoaded() {
        if (loaded) return true;
        try {
            System.loadLibrary("sherpa-onnx-jni");
            loaded = true;
            return true;
        } catch (Throwable t) {
            android.util.Log.e("SherpaEngine", "Unable to load sherpa runtime", t);
            loadError = "超高精度引擎运行库加载失败：" + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : "（" + t.getMessage() + "）");
            return false;
        }
    }

    public static synchronized String recognize(File wav, File modelDir) {
        try {
            float[] samples = readWavMono16k(wav);
            com.k2fsa.sherpa.onnx.FeatureConfig feat = new com.k2fsa.sherpa.onnx.FeatureConfig();
            feat.setSampleRate(16000);
            feat.setFeatureDim(80);
            com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig wh = new com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig();
            wh.setEncoder(new File(modelDir, "small.en-encoder.int8.onnx").getAbsolutePath());
            wh.setDecoder(new File(modelDir, "small.en-decoder.int8.onnx").getAbsolutePath());
            wh.setLanguage("en");
            wh.setTask("transcribe");
            com.k2fsa.sherpa.onnx.OfflineModelConfig mc = new com.k2fsa.sherpa.onnx.OfflineModelConfig();
            mc.setWhisper(wh);
            mc.setTokens(new File(modelDir, "small.en-tokens.txt").getAbsolutePath());
            mc.setNumThreads(2);
            mc.setDebug(false);
            com.k2fsa.sherpa.onnx.OfflineRecognizerConfig rc = new com.k2fsa.sherpa.onnx.OfflineRecognizerConfig();
            rc.setFeatConfig(feat);
            rc.setModelConfig(mc);
            rc.setDecodingMethod("greedy_search");
            com.k2fsa.sherpa.onnx.OfflineRecognizer rec = new com.k2fsa.sherpa.onnx.OfflineRecognizer(null, rc);
            com.k2fsa.sherpa.onnx.OfflineStream stream = rec.createStream();
            stream.acceptWaveform(samples, 16000);
            rec.decode(stream);
            String text = rec.getResult(stream).getText();
            return text == null ? "" : text.trim();
        } catch (Throwable t) {
            android.util.Log.e("SherpaEngine", "recognize failed", t);
            loadError = "超高精度识别失败：" + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : "（" + t.getMessage() + "）");
            return null;
        }
    }

    private static float[] readWavMono16k(File wav) throws Exception {
        byte[] all = Files.readAllBytes(wav.toPath());
        int off = 44;
        for (int i = 12; i + 8 < all.length; i++) {
            if (all[i] == 'd' && all[i + 1] == 'a' && all[i + 2] == 't' && all[i + 3] == 'a') {
                int len = (all[i + 4] & 0xff) | (all[i + 5] & 0xff) << 8 | (all[i + 6] & 0xff) << 16 | (all[i + 7] & 0xff) << 24;
                if (len > 0 && i + 8 + len <= all.length) { off = i + 8; break; }
            }
        }
        int n = (all.length - off) / 2;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            short v = (short) ((all[off + i * 2] & 0xff) | (all[off + i * 2 + 1] << 8));
            out[i] = v / 32768f;
        }
        return out;
    }
}
