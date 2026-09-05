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
    private String lastStatus = "离线发音";
    private AppDatabase appDatabase;
    private WrongBookDb wrongDb;
    private ContentDb contentDb;
    private PrivateFileStore privateFiles;
    private AudioPackManager packs;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        appDatabase = AppDatabase.get(this);
        privateFiles = new PrivateFileStore(this);
        packs = new AudioPackManager(this,privateFiles);
        wrongDb = new WrongBookDb(appDatabase);
        contentDb = new ContentDb(this,appDatabase,privateFiles);
        dbExecutor.execute(() -> { try { contentDb.ensureSeed(); } catch(Exception e){js("contentError",e.getMessage());} });
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
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("确定", (d, w) -> result.confirm()).setCancelable(false).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("确定", (d, w) -> result.confirm()).setNegativeButton("取消", (d, w) -> result.cancel()).setCancelable(false).show();
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
        status("语速已设为 " + Math.round(rate * 100) + "%");
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
        status("本单元语音未下载，请先在顶部下载语音");
    }
    private void playFile(File f, String id, boolean next) { runOnUiThread(() -> { try {
        stopPlayback(); player=new MediaPlayer(); player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        player.setDataSource(f.getAbsolutePath());
        player.setOnPreparedListener(mp->{try{PlaybackParams pp=mp.getPlaybackParams();pp.setSpeed(speechRate);pp.setPitch(1.0f);mp.setPlaybackParams(pp);}catch(Exception ignored){}mark(id);mp.start();js0("playbackStarted");});
        player.setOnCompletionListener(mp->{mp.release();player=null;js0("playbackEnded");if(next)web.evaluateJavascript("window.nextAudio()",null);});
        player.setOnErrorListener((mp,w,e)->{mp.release();player=null;js0("playbackEnded");status("离线语音播放失败");return true;});
        player.prepareAsync();
    } catch(Exception e){ status("离线语音播放失败："+e.getMessage()); } }); }


    private void stopPlayback() {
        js0("playbackEnded");
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }


    private void js0(String function) { runOnUiThread(() -> web.evaluateJavascript("window." + function + "()", null)); }
    private void js(String function, String payload) {
        runOnUiThread(() -> web.evaluateJavascript("window." + function + "(" + org.json.JSONObject.quote(payload) + ")", null));
    }




    private String buildWrongBookHtml() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<html xmlns:x=\"urn:schemas-microsoft-com:office:excel\"><head><meta charset=\"UTF-8\"><style>td,th{border:1px solid #999;padding:6px 10px;font-size:14px}th{background:#dce8f7}h1{font-size:18px}</style></head><body>");
        sb.append("<h1>英语单元练 · 错题本</h1><table><tr><th>序号</th><th>单元</th><th>状态</th><th>错误类型</th><th>内容（英）</th><th>错误单词</th><th>中文</th></tr>");
        android.database.Cursor c = appDatabase.getReadableDatabase().rawQuery("SELECT m.id,m.unit_id,m.stage,m.pronunciation_error,m.writing_error,m.text_en,m.translation FROM mistakes m ORDER BY m.unit_id,m.id", null);
        int n = 0;
        while (c.moveToNext()) {
            n++;
            String id = String.valueOf(c.getLong(0));
            StringBuilder words = new StringBuilder();
            android.database.Cursor w = appDatabase.getReadableDatabase().rawQuery("SELECT word_text FROM mistake_words WHERE mistake_id=? ORDER BY word_index", new String[]{id});
            while (w.moveToNext()) { if (words.length() > 0) words.append("、"); words.append(w.getString(0)); }
            w.close();
            String types = (c.getInt(3) == 1 ? "发音" : "") + (c.getInt(3) == 1 && c.getInt(4) == 1 ? "+" : "") + (c.getInt(4) == 1 ? "书写" : "");
            sb.append("<tr><td>").append(n).append("</td><td>").append(esc(c.getString(1))).append("</td><td>").append("mastered".equals(c.getString(2)) ? "已经掌握" : "正在练习").append("</td><td>").append(esc(types)).append("</td><td>").append(esc(c.getString(5))).append("</td><td>").append(esc(words.toString())).append("</td><td>").append(esc(c.getString(6))).append("</td></tr>");
        }
        c.close();
        sb.append("</table></body></html>");
        return sb.toString();
    }
    private static String esc(String x) { if (x == null) return ""; return x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    public class Bridge {
        @JavascriptInterface public void play(String unitId, String resource, String text, String id, boolean next) { MainActivity.this.play(unitId, resource, text, id, next); }
        @JavascriptInterface public void downloadUnitAudio(String unitId) { packs.download(unitId, new AudioPackManager.Listener(){ public void onProgress(int percent){ js("downloadProgress", packJson(unitId,percent,null,null)); } public void onFinished(boolean ok,String message){ js("downloadFinished", packJson(unitId,-1,ok,message)); } }); }
        private String packJson(String unitId,int percent,Boolean ok,String message){ try{ org.json.JSONObject o=new org.json.JSONObject(); o.put("unitId",unitId); if(percent>=0)o.put("percent",percent); if(ok!=null)o.put("ok",ok); if(message!=null)o.put("message",message); return o.toString(); }catch(Exception e){ return "{\"unitId\":\""+unitId+"\"}"; } }
        @JavascriptInterface public void requestPackState(String unitId) { dbExecutor.execute(() -> { try { js("packState", packs.state(unitId, getAppVersionCode()).toString()); } catch (Exception e) { js("packState", "{\"ready\":false}"); } }); }
        @JavascriptInterface public void deleteUnitAudio(String unitId) { dbExecutor.execute(() -> { try { java.util.Set<String> keep=new java.util.HashSet<>(); android.database.Cursor c=appDatabase.getReadableDatabase().rawQuery("SELECT DISTINCT ci.audio_key FROM content_items ci WHERE ci.id IN (SELECT source_item_id FROM mistakes WHERE source_item_id IS NOT NULL)",null); while(c.moveToNext())keep.add(c.getString(0)); c.close(); packs.delete(unitId,keep); js("audioDeleted",unitId); } catch(Exception e){ js("audioDeleted",unitId); } }); }
        private int getAppVersionCode(){ try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }
    private AppDatabase appDatabase(){ return AppDatabase.get(MainActivity.this); }
        @JavascriptInterface public void stop() { runOnUiThread(() -> stopPlayback()); }
        @JavascriptInterface public void setSpeechRate(float rate) { runOnUiThread(() -> MainActivity.this.setSpeechRate(rate)); }
        @JavascriptInterface public String getSpeechRate() { return String.valueOf(speechRate); }
        @JavascriptInterface public String getAppVersion() { try { android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); return pi.versionName + " (" + pi.getLongVersionCode() + ")"; } catch (Exception e) { return ""; } }
        @JavascriptInterface public void deleteWrong(long id) { dbExecutor.execute(() -> { wrongDb.delete(id); js("wrongDeleted", "已移除"); }); }
        @JavascriptInterface public void requestCatalog() { dbExecutor.execute(() -> { try { contentDb.ensureSeed();js("receiveCatalog",contentDb.catalog()); } catch(Exception e){js("contentError",e.getMessage());} }); }
        @JavascriptInterface public void requestUnit(String unitId) { dbExecutor.execute(() -> { try { contentDb.setState("last_unit_id",unitId);js("receiveUnit",contentDb.unit(unitId)); } catch(Exception e){js("contentError",e.getMessage());} }); }
        @JavascriptInterface public void requestLastUnit() { dbExecutor.execute(() -> js("receiveLastUnit",contentDb.getState("last_unit_id","4A-Starter"))); }
        @JavascriptInterface public void requestWrongList(String stage,String filter,int limit,int offset) { dbExecutor.execute(() -> { try { js("receiveWrongList",wrongDb.list(stage,filter,limit,offset)); } catch(Exception e){ js("wrongBookError",e.getMessage()); } }); }
        @JavascriptInterface public void requestWrongCounts() { dbExecutor.execute(() -> { try { js("receiveWrongCounts",wrongDb.counts()); } catch(Exception e){ js("wrongBookError",e.getMessage()); } }); }
        @JavascriptInterface public void saveMistake(String json) { dbExecutor.execute(() -> { try { wrongDb.saveMistake(json);js("mistakeSaved","已记入错题本"); } catch(Exception e){ js("wrongBookError",e.getMessage()); } }); }
        @JavascriptInterface public void toggleMaster(long id) { dbExecutor.execute(() -> { wrongDb.toggleMaster(id);js("wrongBookChanged","掌握状态已更新"); }); }
        @JavascriptInterface public void archiveWrong(long id) { dbExecutor.execute(() -> { boolean ok=wrongDb.archive(id);js("archiveFinished",ok?"已放入已经掌握":"请先标记为已掌握"); }); }
        @JavascriptInterface public void restoreWrong(long id) { dbExecutor.execute(() -> { wrongDb.restore(id);js("wrongBookChanged","已放回正在练习"); }); }
        @JavascriptInterface public void spellResult(long id,boolean correct,String entered) { dbExecutor.execute(() -> { wrongDb.spellResult(id,correct,entered);js("spellSaved",correct?"correct":"wrong"); }); }
        @JavascriptInterface public void exportWrongBook() { runOnUiThread(() -> { Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/vnd.ms-excel"); i.putExtra(Intent.EXTRA_TITLE,"错题本.xls"); startActivityForResult(i, EXPORT_WRONG); }); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == EXPORT_WRONG) { final Uri uri=data.getData(); dbExecutor.execute(() -> { try { String html=buildWrongBookHtml(); OutputStream out=getContentResolver().openOutputStream(uri); out.write(html.getBytes("UTF-8")); out.close(); js("exportFinished","错题已导出，可用 Excel 打开打印"); } catch (Exception e) { js("exportFinished","导出失败："+e.getMessage()); } }); }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        stopPlayback();
        dbExecutor.shutdown();
        
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
