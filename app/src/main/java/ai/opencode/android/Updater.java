package ai.opencode.android;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Checks GitHub Releases for a newer APK, downloads it and hands it to the system installer. */
final class Updater {
    static final String REPO="LakoMoor/opencode-android";
    static final class Release { String tag, notes, apkUrl; long size; }
    interface Progress { void update(long done,long total); }

    static String current(Context c) { try { return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; } catch(Exception e) { return "0"; } }
    static boolean newer(String remote,String local) {
        String[] a=remote.replaceFirst("^v","").split("\\."),b=local.replaceFirst("^v","").split("\\.");
        for(int i=0;i<Math.max(a.length,b.length);i++) { int x=i<a.length?num(a[i]):0,y=i<b.length?num(b[i]):0; if(x!=y) return x>y; }
        return false;
    }
    private static int num(String s) { try { return Integer.parseInt(s.replaceAll("\\D.*","")); } catch(Exception e) { return 0; } }

    /** Returns the latest release or null when none is published. */
    static Release latest() throws Exception {
        JSONObject json=new JSONObject(get("https://api.github.com/repos/"+REPO+"/releases/latest"));
        Release r=new Release(); r.tag=json.getString("tag_name"); r.notes=json.optString("body");
        JSONArray assets=json.getJSONArray("assets");
        for(int i=0;i<assets.length();i++) { JSONObject a=assets.getJSONObject(i); if(a.getString("name").endsWith(".apk")) { r.apkUrl=a.getString("browser_download_url"); r.size=a.optLong("size"); } }
        return r.apkUrl==null?null:r;
    }
    static String get(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setRequestProperty("Accept","application/vnd.github+json"); c.setRequestProperty("User-Agent","opencode-android");
        if(c.getResponseCode()!=200) throw new IOException("HTTP "+c.getResponseCode());
        try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192]; int n; while((n=in.read(b))>0) out.write(b,0,n); return out.toString("UTF-8"); }
    }
    static File download(Context context,Release release,Progress progress) throws Exception {
        File dir=new File(context.getCacheDir(),"updates"); dir.mkdirs();
        File file=new File(dir,"opencode-android-"+release.tag+".apk");
        HttpURLConnection c=(HttpURLConnection)new URL(release.apkUrl).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent","opencode-android");
        long total=c.getContentLengthLong()>0?c.getContentLengthLong():release.size;
        try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(file)) { byte[] b=new byte[65536]; int n; long done=0,last=0; while((n=in.read(b))>0) { out.write(b,0,n); done+=n; if(done-last>1<<20) { last=done; progress.update(done,total); } } progress.update(done,total); }
        return file;
    }
    /** Opens the system installer; asks for the "install unknown apps" permission first when needed. */
    static boolean install(Activity a,File apk) {
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName()))); return false;
        }
        Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".files",apk);
        a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }
}
