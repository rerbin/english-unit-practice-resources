package com.englishpaper.reader;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.PlaybackParams;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JsResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;

public class MainActivity extends Activity {
    private static final int EXPORT_WRONG = 44;
    private static final String PREFS = "reader_settings";
    private static final String KEY_SPEECH_RATE = "speech_rate";

    private WebView web;
    private MediaPlayer player;
    private SharedPreferences prefs;
    private float speechRate = 1.0f;
    private String lastStatus = "已下载的语音可以离线播放";
    private AppDatabase appDatabase;
    private WrongBookDb wrongDb;
    private ContentDb contentDb;
    private PrivateFileStore privateFiles;
    private AudioPackManager packs;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private static final int REQ_MIC = 45;
    private AudioRecord recorder;
    private Thread recordThread;
    private volatile boolean recording;
    private File readPcm;
    private String pendingReadText;
    private long pendingReadId;
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();
    private Map<String, String[]> phonemeIpa;
    private Map<String, String[]> phonemeNeighbors;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            getWindow().setAttributes(lp);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0xFF315DA8);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        appDatabase = AppDatabase.get(this);
        privateFiles = new PrivateFileStore(this);
        packs = new AudioPackManager(this,privateFiles);
        wrongDb = new WrongBookDb(appDatabase);
        contentDb = new ContentDb(this,appDatabase,privateFiles);
        dbExecutor.execute(() -> { try { contentDb.ensureSeed(); } catch(Exception e){android.util.Log.e("MainActivity","Unable to initialize learning content",e);js("contentError","学习内容暂时无法打开。请重新启动应用；如果仍有问题，请更新或重新安装应用。");} });
        speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f);

        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("知道了", (d, w) -> result.confirm()).setCancelable(false).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("继续", (d, w) -> result.confirm()).setNegativeButton("取消", (d, w) -> result.cancel()).setCancelable(false).show();
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_res/raw/home.html");
    }




    private void setSpeechRate(float rate) {
        if (rate < 0.6f) rate = 0.6f;
        if (rate > 1.4f) rate = 1.4f;
        speechRate = rate;
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate).apply();
        applyRate();
        status("播放速度已调整为 " + Math.round(rate * 100) + "%");
    }
    private void applyRate() {
        if (player != null) { try { PlaybackParams pp = player.getPlaybackParams(); pp.setSpeed(speechRate); pp.setPitch(1.0f); player.setPlaybackParams(pp); } catch (Exception ignored) { } }
    }



    private void status(String message) {
        lastStatus = message;
        if (web != null) runOnUiThread(() -> web.evaluateJavascript("window.ttsStatus(" + org.json.JSONObject.quote(message) + ")", null));
    }

    private void mark(String id) {
        if (web != null) runOnUiThread(() -> web.evaluateJavascript("window.markPlaying(" + org.json.JSONObject.quote(id) + ")", null));
    }

    private void play(String unitId, String resourceName, String text, String id, boolean next) {
        File packFile = packs.fileFor(unitId, resourceName);
        if (packFile != null) { playFile(packFile, id, next); return; }
        if (next) js0("playbackFailed");
        status("还没有下载本单元语音。请点击“下载语音”后再播放。");
    }
    private void playFile(File f, String id, boolean next) { runOnUiThread(() -> { try {
        stopPlayback(); player=new MediaPlayer(); player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        player.setDataSource(f.getAbsolutePath());
        player.setOnPreparedListener(mp->{try{PlaybackParams pp=mp.getPlaybackParams();pp.setSpeed(speechRate);pp.setPitch(1.0f);mp.setPlaybackParams(pp);}catch(Exception ignored){}mark(id);mp.start();js0("playbackStarted");});
        player.setOnCompletionListener(mp->{mp.release();player=null;js0("playbackEnded");if(next)web.evaluateJavascript("window.nextAudio()",null);});
        player.setOnErrorListener((mp,w,e)->{mp.release();player=null;js0("playbackEnded");js0("playbackFailed");status("语音播放失败。请重试；如果仍无法播放，请重新下载本单元语音。");return true;});
        player.prepareAsync();
    } catch(Exception e){ android.util.Log.e("MainActivity","Unable to play offline audio",e); js0("playbackFailed"); status("语音播放失败。请重试；如果仍无法播放，请重新下载本单元语音。"); } }); }


    private void stopPlayback() {
        js0("playbackEnded");
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }


    private Map<String, String[]> loadPhonemeDict() {
        if (phonemeIpa != null) return phonemeIpa;
        Map<String, String[]> ipa = new HashMap<>();
        Map<String, String[]> nb = new HashMap<>();
        try {
            int id = getResources().getIdentifier("phoneme_dict", "raw", getPackageName());
            InputStream in = getResources().openRawResource(id);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) bos.write(b, 0, n);
            in.close();
            org.json.JSONObject root = new org.json.JSONObject(new String(bos.toByteArray(), "UTF-8"));
            for (java.util.Iterator<String> it = root.keys(); it.hasNext(); ) {
                String w = it.next();
                org.json.JSONObject e = root.getJSONObject(w);
                org.json.JSONArray a = e.optJSONArray("ipa");
                if (a != null) { String[] arr = new String[a.length()]; for (int i = 0; i < a.length(); i++) arr[i] = a.getString(i); ipa.put(w, arr); }
                org.json.JSONArray ns = e.optJSONArray("neighbors");
                if (ns != null) { String[] arr = new String[ns.length()]; for (int i = 0; i < ns.length(); i++) arr[i] = ns.getString(i); nb.put(w, arr); }
            }
        } catch (Exception e) { android.util.Log.e("MainActivity", "Unable to load phoneme dict", e); }
        phonemeIpa = ipa; phonemeNeighbors = nb;
        return ipa;
    }

    private String grammarFor(String text) {
        java.util.List<String> ws = ReadAloudScorer.words(text);
        if (ws.size() != 1) return null;
        loadPhonemeDict();
        String[] nb = phonemeNeighbors.get(ws.get(0));
        if (nb == null || nb.length == 0) return null;
        org.json.JSONArray g = new org.json.JSONArray();
        g.put(ws.get(0));
        for (String x : nb) g.put(x);
        return g.toString();
    }

    private static double avgConf(org.json.JSONObject r) {
        try {
            org.json.JSONArray a = r.optJSONArray("result");
            if (a == null || a.length() == 0) return -1;
            double sum = 0; int n = 0;
            for (int i = 0; i < a.length(); i++) { double c = a.getJSONObject(i).optDouble("conf", -1); if (c >= 0) { sum += c; n++; } }
            return n == 0 ? -1 : sum / n;
        } catch (Exception e) { return -1; }
    }

    private static void writeWav(File pcm, File wav) throws IOException {
        byte[] data = java.nio.file.Files.readAllBytes(pcm.toPath());
        try (FileOutputStream out = new FileOutputStream(wav)) {
            out.write(new byte[]{'R','I','F','F'});
            writeInt(out, 36 + data.length);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeInt(out, 16); writeShort(out, (short)1); writeShort(out, (short)1);
            writeInt(out, 16000); writeInt(out, 32000); writeShort(out, (short)2); writeShort(out, (short)16);
            out.write(new byte[]{'d','a','t','a'});
            writeInt(out, data.length);
            out.write(data);
        }
    }
    private static void writeInt(OutputStream o, int v) throws IOException { o.write(v & 0xff); o.write((v >> 8) & 0xff); o.write((v >> 16) & 0xff); o.write((v >> 24) & 0xff); }
    private static void writeShort(OutputStream o, short v) throws IOException { o.write(v & 0xff); o.write((v >> 8) & 0xff); }

    private void startReadAloud(long id, String text) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingReadId = id; pendingReadText = text;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        beginRecording(id, text);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_MIC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pendingReadText != null) beginRecording(pendingReadId, pendingReadText);
            else { pendingReadText = null; js("readAloudState", "denied"); }
        }
    }

    private void beginRecording(long id, String text) {
        pendingReadId = id; pendingReadText = text;
        try {
            stopPlayback();
            int buf = Math.max(AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 6400);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) { recorder.release(); recorder = null; js("readAloudState", "error"); return; }
            readPcm = new File(getCacheDir(), "readaloud.pcm");
            recording = true;
            recorder.startRecording();
            js("readAloudState", "recording");
            final byte[] b = new byte[3200];
            recordThread = new Thread(() -> {
                try (FileOutputStream out = new FileOutputStream(readPcm)) {
                    while (recording) { int n = recorder.read(b, 0, b.length); if (n > 0) out.write(b, 0, n); }
                } catch (Exception ignored) { }
            }, "readaloud-record");
            recordThread.start();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Unable to start recording", e);
            js("readAloudState", "error");
        }
    }

    private void stopReadAloud() {
        if (!recording) return;
        recording = false;
        try { if (recordThread != null) recordThread.join(1500); } catch (Exception ignored) { }
        try { if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) { } recorder.release(); recorder = null; } } catch (Exception ignored) { }
        js("readAloudState", "scoring");
        final String text = pendingReadText;
        final long id = pendingReadId;
        readExecutor.execute(() -> {
            try {
                File wav = new File(getCacheDir(), "readaloud.wav");
                writeWav(readPcm, wav);
                if (!SpeechEngine.isLoaded()) SpeechEngine.load(packs.engineRoot());
                String raw = SpeechEngine.recognize(wav, grammarFor(text));
                org.json.JSONObject r = new org.json.JSONObject(raw);
                String heard = r.optString("text", "").trim();
                double conf = avgConf(r);
                String state; String hint = null;
                String nh = ReadAloudScorer.normalize(heard);
                if (nh.isEmpty()) state = "unclear";
                else if (nh.equals(ReadAloudScorer.normalize(text))) state = "pass";
                else if (ReadAloudScorer.words(text).size() > 1 && ReadAloudScorer.wordMatchRate(text, heard) >= 0.8) state = "pass";
                else { state = "fail"; hint = ReadAloudScorer.phonemeHint(text, heard, loadPhonemeDict()); }
                wav.delete(); if (readPcm != null) readPcm.delete();
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", id); o.put("state", state); o.put("heard", heard); o.put("conf", conf);
                if (hint != null) o.put("hint", hint);
                js("readAloudResult", o.toString());
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Read aloud scoring failed", e);
                js("readAloudState", "error");
            }
        });
    }

    private void js0(String function) { runOnUiThread(() -> web.evaluateJavascript("window." + function + "()", null)); }
    private void js(String function, String payload) {
        runOnUiThread(() -> web.evaluateJavascript("window." + function + "(" + org.json.JSONObject.quote(payload) + ")", null));
    }




    private String buildWrongBookHtml() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<html xmlns:x=\"urn:schemas-microsoft-com:office:excel\"><head><meta charset=\"UTF-8\"><style>td,th{border:1px solid #999;padding:6px 10px;font-size:14px}th{background:#dce8f7}h1{font-size:18px}</style></head><body>");
        String exportedAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date());
        sb.append("<h1>英语单元练 · 错题本</h1><p>导出时间：").append(esc(exportedAt)).append("</p><table><tr><th>序号</th><th>单元</th><th>状态</th><th>练习类型</th><th>英语内容</th><th>需练单词</th><th>中文</th></tr>");
        android.database.Cursor c = appDatabase.getReadableDatabase().rawQuery("SELECT m.id,m.unit_id,m.stage,m.pronunciation_error,m.writing_error,m.text_en,m.translation,COALESCE(c.short_title,c.title,m.unit_title,m.unit_id) FROM mistakes m LEFT JOIN units c ON c.id=m.unit_id ORDER BY COALESCE(c.sort_order,999),m.id", null);
        int n = 0;
        while (c.moveToNext()) {
            n++;
            String id = String.valueOf(c.getLong(0));
            StringBuilder words = new StringBuilder();
            android.database.Cursor w = appDatabase.getReadableDatabase().rawQuery("SELECT word_text FROM mistake_words WHERE mistake_id=? ORDER BY word_index", new String[]{id});
            while (w.moveToNext()) { if (words.length() > 0) words.append("、"); words.append(w.getString(0)); }
            w.close();
            String types = (c.getInt(3) == 1 ? "发音" : "") + (c.getInt(3) == 1 && c.getInt(4) == 1 ? "、" : "") + (c.getInt(4) == 1 ? "拼写" : "");
            sb.append("<tr><td>").append(n).append("</td><td>").append(esc(c.getString(7))).append("</td><td>").append("mastered".equals(c.getString(2)) ? "已掌握" : "正在练习").append("</td><td>").append(esc(types)).append("</td><td>").append(esc(c.getString(5))).append("</td><td>").append(esc(words.toString())).append("</td><td>").append(esc(c.getString(6))).append("</td></tr>");
        }
        c.close();
        sb.append("</table></body></html>");
        return sb.toString();
    }
    private static String esc(String x) { if (x == null) return ""; return x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    public class Bridge {
        @JavascriptInterface public void play(String unitId, String resource, String text, String id, boolean next) { MainActivity.this.play(unitId, resource, text, id, next); }
        @JavascriptInterface public void downloadUnitAudio(String unitId) { packs.download(unitId, new AudioPackManager.Listener(){ public void onProgress(int percent){ js("downloadProgress", packJson(unitId,percent,null,null)); } public void onFinished(boolean ok,String message){ js("downloadFinished", packJson(unitId,-1,ok,message)); } }); }
        @JavascriptInterface public void requestPhonicsState() { dbExecutor.execute(() -> { try { js("phonicsState", packs.baseState().toString()); } catch(Exception ignored){} }); }
        @JavascriptInterface public void downloadPhonicsBase() { js("phonicsFinished", packJson("base",-1,true,"基础音标发音已内置，无需下载")); }
        @JavascriptInterface public void requestVoiceTrials() { js("voiceTrialState", packs.trialState().toString()); }
        @JavascriptInterface public void downloadVoiceTrial(String variant) { packs.downloadTrial(variant, new AudioPackManager.Listener(){ public void onProgress(int percent){ js("voiceTrialProgress", packJson(variant,percent,null,null)); } public void onFinished(boolean ok,String message){ js("voiceTrialFinished", packJson(variant,-1,ok,message)); } }); }
        @JavascriptInterface public void selectVoiceTrial(String variant) { packs.selectVariant(variant); js("voiceTrialState", packs.trialState().toString()); js("voiceTrialSelected", variant); }
        private String packJson(String unitId,int percent,Boolean ok,String message){ try{ org.json.JSONObject o=new org.json.JSONObject(); o.put("unitId",unitId); if(percent>=0)o.put("percent",percent); if(ok!=null)o.put("ok",ok); if(message!=null)o.put("message",message); return o.toString(); }catch(Exception e){ return "{\"unitId\":\""+unitId+"\"}"; } }
        @JavascriptInterface public void requestPackState(String unitId) { dbExecutor.execute(() -> { try { js("packState", packs.state(unitId, getAppVersionCode()).toString()); } catch (Exception e) { js("packState", "{\"ready\":false}"); } }); packs.refreshCatalogAsync(() -> { try { js("packState", packs.state(unitId, getAppVersionCode()).toString()); } catch (Exception ignored) { } }); }
        @JavascriptInterface public void deleteUnitAudio(String unitId) { dbExecutor.execute(() -> { try { java.util.Set<String> keep=new java.util.HashSet<>(); android.database.Cursor c=appDatabase.getReadableDatabase().rawQuery("SELECT DISTINCT ci.audio_key FROM content_items ci WHERE ci.id IN (SELECT source_item_id FROM mistakes WHERE source_item_id IS NOT NULL)",null); while(c.moveToNext())keep.add(c.getString(0)); c.close(); packs.delete(unitId,keep); js("audioDeleted",unitId); } catch(Exception e){ android.util.Log.e("MainActivity","Unable to delete unit audio",e); js("audioDeleteFailed","本单元语音删除失败。请稍后重试。"); } }); }
        private int getAppVersionCode(){ try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }
    private AppDatabase appDatabase(){ return AppDatabase.get(MainActivity.this); }
        @JavascriptInterface public void requestEngineState() { dbExecutor.execute(() -> js("engineState", packs.engineState().toString())); }
        @JavascriptInterface public void downloadEngine() { packs.downloadEngine(new AudioPackManager.Listener(){ public void onProgress(int percent){ js("engineDownloadProgress", packJson("engine",percent,null,null)); } public void onFinished(boolean ok,String message){ js("engineDownloadFinished", packJson("engine",-1,ok,message)); } }); }
        @JavascriptInterface public void startReadAloud(long id, String text) { runOnUiThread(() -> startReadAloud(id, text)); }
        @JavascriptInterface public void stopReadAloud() { runOnUiThread(() -> stopReadAloud()); }
        @JavascriptInterface public void cancelReadAloud() { runOnUiThread(() -> { recording = false; try { if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) { } recorder.release(); recorder = null; } } catch (Exception ignored) { } if (readPcm != null) readPcm.delete(); js("readAloudState", "cancelled"); }); }
        @JavascriptInterface public void setMastered(long id, boolean mastered) { dbExecutor.execute(() -> { wrongDb.setMastered(id, mastered); js("masteredSet", id + "|" + mastered); }); }
        @JavascriptInterface public void stop() { runOnUiThread(() -> stopPlayback()); }
        @JavascriptInterface public void setSpeechRate(float rate) { runOnUiThread(() -> MainActivity.this.setSpeechRate(rate)); }
        @JavascriptInterface public String getSpeechRate() { return String.valueOf(speechRate); }
        @JavascriptInterface public String getAppVersion() { try { android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); return pi.versionName + " (" + pi.getLongVersionCode() + ")"; } catch (Exception e) { return ""; } }
        @JavascriptInterface public void deleteWrong(long id) { dbExecutor.execute(() -> { wrongDb.delete(id); js("wrongDeleted", "这道错题已永久删除，无法恢复。"); }); }
        @JavascriptInterface public void requestCatalog() { dbExecutor.execute(() -> { try { contentDb.ensureSeed();js("receiveCatalog",contentDb.catalog()); } catch(Exception e){android.util.Log.e("MainActivity","Unable to initialize learning content",e);js("contentError","学习内容暂时无法打开。请重新启动应用；如果仍有问题，请更新或重新安装应用。");} }); }
        @JavascriptInterface public void requestUnit(String unitId) { dbExecutor.execute(() -> { try { contentDb.setState("last_unit_id",unitId);js("receiveUnit",contentDb.unit(unitId)); } catch(Exception e){android.util.Log.e("MainActivity","Unable to initialize learning content",e);js("contentError","学习内容暂时无法打开。请重新启动应用；如果仍有问题，请更新或重新安装应用。");} }); }
        @JavascriptInterface public void requestLastUnit() { dbExecutor.execute(() -> js("receiveLastUnit",contentDb.getState("last_unit_id","4A-Starter"))); }
        @JavascriptInterface public void requestWrongAll(String stage,String filter,String unitId,int offset) { dbExecutor.execute(() -> { try { String scope=unitId==null?"":unitId; org.json.JSONObject o=new org.json.JSONObject(); o.put("list",new org.json.JSONObject(wrongDb.list(stage,filter,scope,50,offset))); o.put("counts",new org.json.JSONObject(wrongDb.counts(scope))); o.put("totalCounts",new org.json.JSONObject(wrongDb.counts(""))); o.put("stage",stage); o.put("filter",filter); o.put("unitId",scope); js("receiveWrongAll",o.toString()); } catch(Exception e){ android.util.Log.e("MainActivity","Unable to load wrong book",e); js("wrongBookError","错题本暂时无法打开。请稍后重试。"); } }); }
        @JavascriptInterface public void saveMistake(String json) { dbExecutor.execute(() -> { try { wrongDb.saveMistake(json);js("mistakeSaved","已加入错题本，可以稍后继续练习。"); } catch(Exception e){ android.util.Log.e("MainActivity","Unable to save mistake",e); js("wrongBookError","暂时无法加入错题本。请稍后重试。"); } }); }
        @JavascriptInterface public void toggleMaster(long id) { dbExecutor.execute(() -> { boolean mastered=wrongDb.toggleMaster(id);js("wrongBookChanged",mastered?"已标记为“已掌握”。":"已取消“已掌握”标记。"); }); }
        @JavascriptInterface public void archiveWrong(long id) { dbExecutor.execute(() -> { boolean ok=wrongDb.archive(id);js("archiveFinished",ok?"已移到“已掌握”。":"请先标记为“已掌握”，再移出。"); }); }
        @JavascriptInterface public void restoreWrong(long id) { dbExecutor.execute(() -> { wrongDb.restore(id);js("wrongBookChanged","已移回“正在练习”，可以继续复习。"); }); }
        @JavascriptInterface public void spellResult(long id,boolean correct,String entered) { dbExecutor.execute(() -> { wrongDb.spellResult(id,correct,entered);js("spellSaved",correct?"correct":"wrong"); }); }
        @JavascriptInterface public void exportWrongBook() { runOnUiThread(() -> { Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/vnd.ms-excel"); String stamp=new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm",java.util.Locale.US).format(new java.util.Date()); i.putExtra(Intent.EXTRA_TITLE,"错题本_"+stamp+".xls"); startActivityForResult(i, EXPORT_WRONG); }); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == EXPORT_WRONG) { final Uri uri=data.getData(); dbExecutor.execute(() -> { try { String html=buildWrongBookHtml(); OutputStream out=getContentResolver().openOutputStream(uri); out.write(html.getBytes("UTF-8")); out.close(); js("exportFinished","错题本已保存，可以用表格应用打开或打印。"); } catch (Exception e) { android.util.Log.e("MainActivity","Unable to export wrong book",e); js("exportFinished","导出失败。请重新选择保存位置，并确认设备有足够的存储空间。"); } }); }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        stopPlayback();
        recording = false;
        try { if (recorder != null) { recorder.release(); recorder = null; } } catch (Exception ignored) { }
        readExecutor.shutdown();
        dbExecutor.shutdown();
        
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
