package com.englishpaper.reader;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads, verifies and serves per-unit offline audio packs. */
public final class AudioPackManager {
    public interface Listener { void onProgress(int percent); void onFinished(boolean ok, String message); }

    private static final String[] CATALOG_URLS = {
        "https://raw.githubusercontent.com/rerbin/english-unit-practice-resources/main/catalog.json",
        "https://gitee.com/rerbin/english-unit-practice-resources/raw/master/catalog.json"
    };

    private final Context context;
    private final PrivateFileStore files;
    private final File packsRoot, baseRoot;
    private final android.content.SharedPreferences trialPrefs;
    private static final String TRIAL_PREF = "voice_trial_variant";
    private final Map<String, Map<String,String>> manifestCache = new ConcurrentHashMap<>();

    public AudioPackManager(Context context, PrivateFileStore files) {
        this.context = context.getApplicationContext();
        this.files = files;
        packsRoot = files.packsRoot(); baseRoot = new File(packsRoot, "phonics-base"); if (!baseRoot.exists()) baseRoot.mkdirs();
        trialPrefs = context.getSharedPreferences("voice_trial", 0);
        if (!packsRoot.exists()) packsRoot.mkdirs();
    }

    public File packDir(String unitId) { return new File(packsRoot, safe(unitId)); }

    public boolean isReady(String unitId) { return new File(packDir(unitId), "manifest.json").isFile(); }

    public int installedVersion(String unitId) {
        try { return manifestRoot(unitId).optInt("packageVersion", 0); } catch (Exception e) { return 0; }
    }

    public long packSize(String unitId) { return dirSize(packDir(unitId)); }

    /** Resolve an audio key to an absolute playable file, or null. */
    public File fileFor(String unitId, String audioKey) {
        if (audioKey == null || audioKey.isEmpty()) return null;
        if (audioKey.startsWith("phoneme:")) { try { String rel=keyMapFor(baseRoot,"phonics-base").get(audioKey); if(rel!=null){File f=new File(baseRoot,rel);if(f.isFile())return f;} } catch(Exception ignored){} }
        String variant = selectedVariant();
        if (!"default".equals(variant)) {
            try {
                File dir = trialPackDir(variant, unitId);
                String rel = keyMapFor(dir, "trial:" + variant + ":" + unitId).get(audioKey);
                File f = rel == null ? null : new File(dir, rel);
                if (f != null && f.isFile()) return f;
            } catch (Exception ignored) { }
        }
        try {
            String rel = keyMap(unitId).get(audioKey);
            File f = rel == null ? null : new File(packDir(unitId), rel);
            return f != null && f.isFile() ? f : null;
        } catch (Exception e) { return null; }
    }

    public synchronized Map<String,String> keyMap(String unitId) throws Exception { return keyMapFor(packDir(unitId), "default:" + unitId); }
    private synchronized Map<String,String> keyMapFor(File dir, String cacheKey) throws Exception {
        Map<String,String> cached = manifestCache.get(cacheKey);
        if (cached != null) return cached;
        File mf = new File(dir, "manifest.json");
        JSONObject root = new JSONObject(readAll(new FileInputStream(mf)));
        Map<String,String> map = new HashMap<>();
        JSONArray items = root.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) { JSONObject it = items.getJSONObject(i); map.put(it.getString("audioKey"), it.getString("file")); }
        manifestCache.put(cacheKey, map);
        return map;
    }
    private JSONObject manifestRoot(String unitId) throws Exception {
        File mf = new File(packDir(unitId), "manifest.json");
        return new JSONObject(readAll(new FileInputStream(mf)));
    }

    public boolean baseReady(){return new File(baseRoot,"manifest.json").isFile();}
    public void downloadBase(Listener listener){new Thread(()->{try{File part=new File(packsRoot,"phonics-base.part");if(!downloadResumable("https://raw.githubusercontent.com/rerbin/english-unit-practice-resources/main/phonics-base-v1.zip",part,3127173L,listener)){listener.onFinished(false,"基础发音包下载未完成");return;}File stage=new File(files.importStagingRoot(),"phonics-base");if(stage.exists())deleteTree(stage);stage.mkdirs();try(ZipInputStream z=new ZipInputStream(new FileInputStream(part))){ZipEntry e;byte[] b=new byte[32768];while((e=z.getNextEntry())!=null){if(e.isDirectory())continue;File o=new File(stage,e.getName()).getCanonicalFile();if(!o.getPath().startsWith(stage.getCanonicalPath()+File.separator))throw new SecurityException("非法路径");o.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(o)){int n;while((n=z.read(b))!=-1)out.write(b,0,n);}}}deleteTree(baseRoot);copyTree(stage,baseRoot);deleteTree(stage);part.delete();manifestCache.remove("phonics-base");listener.onFinished(true,"基础发音包下载成功");}catch(Exception e){listener.onFinished(false,"基础包下载失败："+e.getMessage());}},"phonics-base-download").start();}

    public String selectedVariant() { return trialPrefs.getString(TRIAL_PREF, "default"); }
    public void selectVariant(String variant) { trialPrefs.edit().putString(TRIAL_PREF, variant).apply(); }
    private File trialRoot(String variant) { return new File(new File(packsRoot, "voice-trial"), safe(variant)); }
    private File trialPackDir(String variant, String unitId) { return new File(trialRoot(variant), safe(unitId)); }
    public boolean isTrialReady(String variant) { return new File(trialPackDir(variant, "4A-Starter"), "manifest.json").isFile() && new File(trialPackDir(variant, "4A-U1"), "manifest.json").isFile(); }
    public JSONObject trialState() {
        JSONObject o = new JSONObject();
        try { o.put("selected", selectedVariant()); for (String v : new String[]{"piper","kokoro","sonia","cori"}) o.put(v, isTrialReady(v)); } catch (Exception ignored) { }
        return o;
    }
    private String trialUrl(String variant) { return "https://raw.githubusercontent.com/rerbin/english-unit-practice-resources/main/voice-trial-" + variant + "-v1.zip"; }
    private long trialSize(String variant) { if ("piper".equals(variant)) return 8876658L; if ("kokoro".equals(variant)) return 9252066L; if ("sonia".equals(variant)) return 2120307L; if ("cori".equals(variant)) return 9236968L; return -1L; }
    public void downloadTrial(String variant, Listener listener) {
        new Thread(() -> {
            try {
                if (!"piper".equals(variant) && !"kokoro".equals(variant) && !"sonia".equals(variant) && !"cori".equals(variant)) throw new IOException("未知语音方案");
                File part = new File(packsRoot, "trial-" + safe(variant) + ".part");
                if (!downloadResumable(trialUrl(variant), part, trialSize(variant), listener)) { listener.onFinished(false, "下载未完成，稍后可继续下载"); return; }
                installTrial(part, variant); part.delete(); listener.onFinished(true, "语音方案下载成功，可用于试听");
            } catch (Exception e) { listener.onFinished(false, "下载失败：" + e.getMessage()); }
        }, "voice-trial-download").start();
    }
    private void installTrial(File zip, String variant) throws IOException {
        File stage = new File(files.importStagingRoot(), "trial-" + safe(variant)); if (stage.exists()) deleteTree(stage); if (!stage.mkdirs()) throw new IOException("无法创建临时目录");
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e; byte[] b = new byte[32768];
            while ((e = zin.getNextEntry()) != null) { if (e.isDirectory()) continue; File out = new File(stage, e.getName()).getCanonicalFile(); if (!out.getPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new SecurityException("非法路径"); File parent = out.getParentFile(); if (!parent.exists()) parent.mkdirs(); try (OutputStream os = new FileOutputStream(out)) { int n; while ((n = zin.read(b)) != -1) os.write(b, 0, n); } }
        }
        if (!new File(stage, "4A-Starter/manifest.json").isFile() || !new File(stage, "4A-U1/manifest.json").isFile()) { deleteTree(stage); throw new IOException("对比包缺少单元清单"); }
        File dest = trialRoot(variant); if (dest.exists()) deleteTree(dest); copyTree(stage, dest); deleteTree(stage);
        manifestCache.remove("trial:" + variant + ":4A-Starter"); manifestCache.remove("trial:" + variant + ":4A-U1");
    }

    public void delete(String unitId, Set<String> keepAudioKeys) {
        Map<String,String> map = manifestCache.remove(unitId);
        File dir = packDir(unitId);
        if (map == null) { try { map = keyMap(unitId); } catch (Exception e) { map = new HashMap<>(); } }
        Set<String> keepFiles = new HashSet<>();
        if (keepAudioKeys != null) for (String k : keepAudioKeys) { String f = map.get(k); if (f != null) keepFiles.add(f); }
        File audioDir = new File(dir, "audio");
        File[] fs = audioDir.listFiles();
        if (fs != null) for (File f : fs) { String rel = "audio/" + f.getName(); if (!keepFiles.contains(rel)) f.delete(); }
        new File(dir, "manifest.json").delete();
        new File(packsRoot, safe(unitId) + ".part").delete();
    }

    private JSONObject catalogCache; private long catalogCacheAt;
    private final java.util.concurrent.ExecutorService netExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final String PREF_CATALOG = "pack_catalog_json";
    private static final String PREF_CATALOG_AT = "pack_catalog_at";
    public JSONObject fetchCatalog() {
        if (catalogCache != null && System.currentTimeMillis() - catalogCacheAt < 300_000) return catalogCache;
        JSONObject d = readDiskCatalog();
        if (d != null) { catalogCache = d; catalogCacheAt = System.currentTimeMillis(); return d; }
        return null;
    }
    private JSONObject readDiskCatalog() {
        try { android.content.SharedPreferences sp = context.getSharedPreferences("pack_catalog", 0); String json = sp.getString(PREF_CATALOG, null); return json == null ? null : new JSONObject(json); } catch (Exception e) { return null; }
    }
    private void writeDiskCatalog(JSONObject c) {
        try { context.getSharedPreferences("pack_catalog", 0).edit().putString(PREF_CATALOG, c.toString()).putLong(PREF_CATALOG_AT, System.currentTimeMillis()).apply(); } catch (Exception ignored) { }
    }
    public JSONObject fetchCatalogNetwork() {
        for (String url : CATALOG_URLS) { JSONObject c = fetchJson(url); if (c != null) { catalogCache = c; catalogCacheAt = System.currentTimeMillis(); writeDiskCatalog(c); return c; } }
        return null;
    }
    /** Stale-while-revalidate: background refresh, never on the DB executor. */
    public void refreshCatalogAsync(final Runnable onDone) {
        netExecutor.execute(() -> { JSONObject c = fetchCatalogNetwork(); if (c != null && onDone != null) onDone.run(); });
    }
    private static JSONObject catalogUnit(JSONObject catalog, String unitId) throws Exception {
        JSONArray units = catalog.getJSONArray("units");
        for (int i = 0; i < units.length(); i++) { JSONObject u = units.getJSONObject(i); if (unitId.equals(u.getString("unitId"))) return u; }
        return null;
    }
    /** Local + catalog state for the UI banner. */
    public JSONObject state(String unitId, int appVersionCode) {
        JSONObject o = new JSONObject();
        try {
            int installed = installedVersion(unitId);
            o.put("ready", isReady(unitId)); o.put("installedVersion", installed);
            JSONObject catalog = fetchCatalog();
            if (catalog != null) {
                JSONObject unit = catalogUnit(catalog, unitId);
                if (unit != null) {
                    int latest = unit.optInt("audioVersion", 1);
                    o.put("latestVersion", latest);
                    o.put("updateAvailable", installed > 0 && latest > installed);
                    o.put("needsAppUpdate", unit.optInt("minAppVersionCode", 0) > appVersionCode);
                    o.put("size", unit.optLong("size", 0));
                }
            }
        } catch (Exception e) { }
        return o;
    }

    public void download(String unitId, Listener listener) {
        new Thread(() -> {
            try {
                JSONObject catalog = fetchCatalog();
                if (catalog == null) catalog = fetchCatalogNetwork();
                if (catalog == null) { listener.onFinished(false, "无法获取语音目录，请检查网络"); return; }
                JSONObject unit = catalogUnit(catalog, unitId);
                if (unit == null) { listener.onFinished(false, "目录中没有该单元的语音包"); return; }
                String sha = unit.getString("sha256"); long size = unit.optLong("size", -1);
                int installed = installedVersion(unitId); int latest = unit.optInt("audioVersion", 1);
                if (installed > 0 && latest > installed) {
                    JSONObject nm = null; JSONObject mu = unit.optJSONObject("manifestUrl");
                    if (mu != null) for (String k : new String[]{"gitee", "github"}) { nm = fetchJson(mu.optString(k)); if (nm != null) break; }
                    if (nm != null) {
                        JSONArray items = nm.getJSONArray("items");
                        java.util.List<JSONObject> missing = new java.util.ArrayList<>(); long missingBytes = 0;
                        for (int i = 0; i < items.length(); i++) { JSONObject it = items.getJSONObject(i); File f = new File(packDir(unitId), it.getString("file")); if (!f.isFile()) { missing.add(it); missingBytes += it.optLong("size", 0); } }
                        if (missingBytes <= 1_500_000 && downloadMissing(unitId, mu, missing, listener)) {
                            writeManifest(unitId, nm);
                            listener.onFinished(true, "语音已更新到 v" + nm.optInt("packageVersion", latest));
                            return;
                        }
                    }
                }
                JSONObject mirrors = unit.getJSONObject("mirrors");
                String[] urls = { mirrors.optString("gitee"), mirrors.optString("github") };
                File part = new File(packsRoot, safe(unitId) + ".part");
                boolean got = false;
                for (String u : urls) {
                    if (u == null || u.isEmpty()) continue;
                    got = downloadResumable(u, part, size, listener);
                    if (got) break;
                }
                if (!got) { listener.onFinished(false, "下载未完成，稍后可继续下载"); return; }
                String actual = sha256(part);
                if (!actual.equalsIgnoreCase(sha)) { part.delete(); listener.onFinished(false, "文件校验失败，请重试"); return; }
                install(part, unitId);
                part.delete();
                listener.onFinished(true, "语音已下载，可离线使用");
            } catch (Exception e) {
                listener.onFinished(false, "下载失败：" + e.getMessage());
            }
        }, "audio-pack-download").start();
    }

    /** Incremental: fetch only missing audio files (small updates like a new letter sound). */
    private boolean downloadMissing(String unitId, JSONObject manifestUrls, java.util.List<JSONObject> missing, Listener listener) throws Exception {
        if (manifestUrls == null) return false;
        String baseG = manifestUrls.optString("github", "").replace("manifest.json", "");
        String baseC = manifestUrls.optString("gitee", "").replace("manifest.json", "");
        int done = 0;
        for (JSONObject it : missing) {
            String rel = it.getString("file"); String want = it.optString("sha256", "");
            File dest = new File(packDir(unitId), rel);
            File parent = dest.getParentFile(); if (!parent.exists()) parent.mkdirs();
            boolean got = false;
            for (String base : new String[]{baseC, baseG}) {
                if (base.isEmpty()) continue;
                try {
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(base + rel).openConnection();
                    con.setConnectTimeout(10000); con.setReadTimeout(30000);
                    if (con.getResponseCode() / 100 != 2) { con.disconnect(); continue; }
                    File tmp = new File(parent, dest.getName() + ".tmp");
                    try (java.io.InputStream in = con.getInputStream(); java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                        byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n);
                    }
                    con.disconnect();
                    if (want.isEmpty() || sha256(tmp).equalsIgnoreCase(want)) { if (dest.exists()) dest.delete(); tmp.renameTo(dest); got = true; } else tmp.delete();
                } catch (Exception e) { }
                if (got) break;
            }
            if (!got) return false;
            done++; listener.onProgress(done * 100 / missing.size());
        }
        return true;
    }
    private void writeManifest(String unitId, JSONObject nm) throws Exception {
        File mf = new File(packDir(unitId), "manifest.json");
        File parent = mf.getParentFile(); if (!parent.exists()) parent.mkdirs();
        java.io.FileWriter w = new java.io.FileWriter(mf); w.write(nm.toString(2)); w.close();
        manifestCache.remove(unitId);
    }

    /** Range-based resumable download; keeps partial file for later continuation. */
    private boolean downloadResumable(String url, File part, long expectedSize, Listener listener) {
        HttpURLConnection con = null;
        try {
            long have = part.isFile() ? part.length() : 0;
            if (expectedSize > 0 && have >= expectedSize) have = 0;
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10000); con.setReadTimeout(30000);
            con.setInstanceFollowRedirects(true);
            if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
            int code = con.getResponseCode();
            if (code / 100 != 2) return false;
            boolean append = code == 206;
            if (!append) have = 0;
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(part, append)) {
                byte[] b = new byte[32768]; int n; long done = have; int last = -1;
                while ((n = in.read(b)) != -1) {
                    out.write(b, 0, n); done += n;
                    int pct = expectedSize > 0 ? (int) (done * 100 / expectedSize) : -1;
                    if (pct != last) { last = pct; listener.onProgress(pct); }
                }
            }
            return expectedSize <= 0 || part.length() == expectedSize;
        } catch (Exception e) {
            return false;
        } finally { if (con != null) con.disconnect(); }
    }

    private void install(File zip, String unitId) throws IOException {
        File dir = packDir(unitId);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录");
        File stage = new File(files.importStagingRoot(), "extract-" + safe(unitId));
        if (stage.exists()) deleteTree(stage);
        if (!stage.mkdirs()) throw new IOException("无法创建解压目录");
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e; byte[] b = new byte[32768];
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File out = new File(stage, e.getName()).getCanonicalFile();
                if (!out.getPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new SecurityException("非法路径");
                File parent = out.getParentFile(); if (!parent.exists()) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) { int n; while ((n = zin.read(b)) != -1) os.write(b, 0, n); }
            }
        }
        File mf = new File(stage, "manifest.json");
        if (!mf.isFile()) { deleteTree(stage); throw new IOException("语音包缺少清单"); }
        copyTree(stage, dir);
        deleteTree(stage);
        manifestCache.remove(unitId);
    }

    private JSONObject fetchJson(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000); con.setReadTimeout(15000);
            if (con.getResponseCode() / 100 != 2) return null;
            return new JSONObject(readAll(con.getInputStream()));
        } catch (Exception e) { return null; } finally { if (con != null) con.disconnect(); }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        in.close(); return out.toString("UTF-8");
    }
    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n); }
        StringBuilder s = new StringBuilder(); for (byte x : md.digest()) s.append(String.format(Locale.US, "%02x", x));
        return s.toString();
    }
    private static String safe(String x) { return x.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static long dirSize(File d) { File[] fs = d.listFiles(); long t = 0; if (fs != null) for (File f : fs) t += f.isDirectory() ? dirSize(f) : f.length(); return t; }
    private static void deleteTree(File d) { File[] fs = d.listFiles(); if (fs != null) for (File f : fs) { if (f.isDirectory()) deleteTree(f); f.delete(); } d.delete(); }
    private static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) { if (!dst.exists()) dst.mkdirs(); File[] fs = src.listFiles(); if (fs != null) for (File f : fs) copyTree(f, new File(dst, f.getName())); }
        else { try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); } }
    }
}
