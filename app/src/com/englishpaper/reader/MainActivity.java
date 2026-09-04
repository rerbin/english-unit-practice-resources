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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_JSON = 41;
    private static final int CREATE_BACKUP = 42;
    private static final int RESTORE_BACKUP = 43;
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
        dbExecutor.execute(() -> { try { contentDb.ensureSeed();js("contentReady","ready"); } catch(Exception e){js("contentError",e.getMessage());} });
        speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f);

        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new WebViewClient());
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
        player.setOnPreparedListener(mp->{try{PlaybackParams pp=mp.getPlaybackParams();pp.setSpeed(speechRate);pp.setPitch(1.0f);mp.setPlaybackParams(pp);}catch(Exception ignored){}mark(id);mp.start();status("正在播放离线语音");});
        player.setOnCompletionListener(mp->{mp.release();player=null;if(next)web.evaluateJavascript("window.nextAudio()",null);});
        player.setOnErrorListener((mp,w,e)->{mp.release();player=null;status("离线语音播放失败");return true;});
        player.prepareAsync();
    } catch(Exception e){ status("离线语音播放失败："+e.getMessage()); } }); }


    private void stopPlayback() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private String read(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] data = new byte[4096]; int count;
        while ((count = input.read(data)) != -1) output.write(data, 0, count);
        input.close();
        return output.toString("UTF-8");
    }

    private void js(String function, String payload) {
        runOnUiThread(() -> web.evaluateJavascript("window." + function + "(" + org.json.JSONObject.quote(payload) + ")", null));
    }

    private void chooseBackupDestination() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"英语单元练-完整备份.zip");startActivityForResult(i,CREATE_BACKUP);
    }
    private void chooseBackupToRestore() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,RESTORE_BACKUP);
    }
    private void writeBackup(Uri uri) {
        dbExecutor.execute(() -> { try {
            org.json.JSONObject dataRoot=new org.json.JSONObject(wrongDb.backup());dataRoot.put("contentTables",contentDb.backupTables());dataRoot.put("backupKind","complete");
            try(ZipOutputStream zip=new ZipOutputStream(getContentResolver().openOutputStream(uri,"wt"))){zip.putNextEntry(new ZipEntry("data.json"));zip.write(dataRoot.toString(2).getBytes("UTF-8"));zip.closeEntry();zipDirectory(zip,privateFiles.contentRoot(),"files/");}
            js("backupFinished","完整备份已保存");
        } catch(Exception e){js("backupFinished","备份失败："+e.getMessage());} });
    }
    private static void zipDirectory(ZipOutputStream zip,File dir,String prefix)throws IOException{File[] files=dir.listFiles();if(files==null)return;for(File file:files){String name=prefix+file.getName();if(file.isDirectory())zipDirectory(zip,file,name+"/");else{zip.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();}}}

    private void restoreBackup(Uri uri) {
        dbExecutor.execute(() -> {File stage=null;try{
            stage=new File(privateFiles.importStagingRoot(),"restore-"+System.currentTimeMillis());if(!stage.exists()&&!stage.mkdirs())throw new IOException("无法创建还原目录");File dataFile=new File(stage,"data.json");
            try(ZipInputStream zip=new ZipInputStream(getContentResolver().openInputStream(uri))){ZipEntry entry;byte[] b=new byte[16384];while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(name.startsWith("/")||name.contains(".."))throw new SecurityException("备份包含非法路径");File out=new File(stage,name).getCanonicalFile();if(!out.getPath().startsWith(stage.getCanonicalPath()+File.separator))throw new SecurityException("备份路径越界");if(entry.isDirectory()){out.mkdirs();continue;}File parent=out.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("无法创建还原目录");try(OutputStream os=new FileOutputStream(out)){int n;while((n=zip.read(b))!=-1)os.write(b,0,n);}}}
            String raw=readFile(dataFile);org.json.JSONObject root=new org.json.JSONObject(raw);org.json.JSONArray content=root.getJSONArray("contentTables");contentDb.restoreTables(content);int count=wrongDb.restoreBackup(raw);File payload=new File(stage,"files");if(payload.exists())privateFiles.copyTreeIntoStore(payload);js("restoreFinished","已还原 "+count+" 条错题、全部单元和离线文件");
        }catch(Exception e){js("restoreFinished","还原失败："+e.getMessage());}finally{privateFiles.cleanupStaging();}});
    }
    private static String readFile(File file)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}


    public class Bridge {
        @JavascriptInterface public void play(String unitId, String resource, String text, String id, boolean next) { MainActivity.this.play(unitId, resource, text, id, next); }
        @JavascriptInterface public void downloadUnitAudio(String unitId) { packs.download(unitId, new AudioPackManager.Listener(){ public void onProgress(int percent){ js("downloadProgress", packJson(unitId,percent,null,null)); } public void onFinished(boolean ok,String message){ js("downloadFinished", packJson(unitId,-1,ok,message)); } }); }
        private String packJson(String unitId,int percent,Boolean ok,String message){ try{ org.json.JSONObject o=new org.json.JSONObject(); o.put("unitId",unitId); if(percent>=0)o.put("percent",percent); if(ok!=null)o.put("ok",ok); if(message!=null)o.put("message",message); return o.toString(); }catch(Exception e){ return "{\"unitId\":\""+unitId+"\"}"; } }
        @JavascriptInterface public void requestPackState(String unitId) { dbExecutor.execute(() -> { try { js("packState", packs.state(unitId, getAppVersionCode()).toString()); } catch (Exception e) { js("packState", "{\"ready\":false}"); } }); }
        @JavascriptInterface public String getAudioState(String unitId) { try { org.json.JSONObject o=new org.json.JSONObject(); o.put("ready",packs.isReady(unitId)); o.put("version",packs.installedVersion(unitId)); o.put("size",packs.packSize(unitId)); return o.toString(); } catch(Exception e){ return "{\"ready\":false}"; } }
        @JavascriptInterface public void deleteUnitAudio(String unitId) { dbExecutor.execute(() -> { try { java.util.Set<String> keep=new java.util.HashSet<>(); android.database.Cursor c=appDatabase().getReadableDatabase().rawQuery("SELECT DISTINCT ci.audio_key FROM content_items ci WHERE ci.id IN (SELECT source_item_id FROM mistakes WHERE source_item_id IS NOT NULL)",null); while(c.moveToNext())keep.add(c.getString(0)); c.close(); packs.delete(unitId,keep); js("audioDeleted",unitId); } catch(Exception e){ js("audioDeleted",unitId); } }); }
        private int getAppVersionCode(){ try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }
    private AppDatabase appDatabase(){ return AppDatabase.get(MainActivity.this); }
        @JavascriptInterface public void stop() { runOnUiThread(() -> stopPlayback()); }
        @JavascriptInterface public void setSpeechRate(float rate) { runOnUiThread(() -> MainActivity.this.setSpeechRate(rate)); }
        @JavascriptInterface public String getSpeechRate() { return String.valueOf(speechRate); }
        @JavascriptInterface public String getAppVersion() { try { android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); return pi.versionName + " (" + pi.versionCode + ")"; } catch (Exception e) { return ""; } }
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
        @JavascriptInterface public void backupData() { runOnUiThread(() -> chooseBackupDestination()); }
        @JavascriptInterface public void restoreData() { runOnUiThread(() -> chooseBackupToRestore()); }
        @JavascriptInterface public void importPaper() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, PICK_JSON);
            });
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_JSON) {
            final Uri uri=data.getData();dbExecutor.execute(() -> {try{String id=contentDb.importUnit(read(uri));js("unitImported",id);}catch(Exception e){js("contentError","导入失败："+e.getMessage());}});
        } else if (request == CREATE_BACKUP) writeBackup(data.getData());
        else if (request == RESTORE_BACKUP) restoreBackup(data.getData());
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
