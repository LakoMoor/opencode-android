package ai.opencode.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import android.view.WindowManager;
import android.graphics.Color;
import android.content.pm.PackageManager;
import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Displays the unchanged UI served by the local upstream CLI; no replacement chat UI. */
public class WebActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> files;
    private PermissionRequest microphone;
    private byte[] download;
    private final java.util.concurrent.ExecutorService io=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!RuntimeService.ready){finish();return;}
        web=new WebView(this);web.setBackgroundColor(Color.rgb(16,18,17));web.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);frame.setBackgroundColor(Color.rgb(16,18,17));frame.addView(web);setContentView(frame);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(false);settings.setTextZoom(100);settings.setCacheMode(WebSettings.LOAD_DEFAULT);settings.setOffscreenPreRaster(true);settings.setSupportZoom(false);settings.setBuiltInZoomControls(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(true);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Downloads(),"AndroidDownload");
        web.setDownloadListener((url,agent,disposition,mime,length)->{
            String name=URLUtil.guessFileName(url,disposition,mime);
            if(url.startsWith("blob:")) {
                String script="fetch("+JSONObject.quote(url)+").then(r=>r.blob()).then(b=>{if(b.size>26214400)throw Error('File too large');const r=new FileReader();r.onload=()=>AndroidDownload.save("+JSONObject.quote(name)+",b.type,r.result.split(',')[1]);r.readAsDataURL(b)}).catch(()=>AndroidDownload.failed())";
                web.evaluateJavascript(script,null);return;
            }
            if(!isLocal(Uri.parse(url)))return;
            String cookie=android.webkit.CookieManager.getInstance().getCookie(url);
            io.execute(()->{try {
                HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);if(cookie!=null)connection.setRequestProperty("Cookie",cookie);
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){if(out.size()+n>26214400)throw new IOException("File too large");out.write(b,0,n);}byte[] bytes=out.toByteArray();runOnUiThread(()->offerDownload(name,mime,bytes)); }finally{connection.disconnect();}
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,L.t("Could not save the file","Не удалось сохранить файл"),Toast.LENGTH_LONG).show());}});
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){if(isLocal(Uri.parse(url))&&!Uri.parse(url).getPath().startsWith("/auth/"))RuntimeService.paired=true;}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if(isLocal(uri))return false;
                if("https".equals(uri.getScheme())||"http".equals(uri.getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){Toast.makeText(WebActivity.this,L.t("No browser available to open the link","Нет браузера для открытия ссылки"),Toast.LENGTH_SHORT).show();}}
                return true;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())Toast.makeText(WebActivity.this,L.t("The local server is unavailable. Check the environment log.","Локальный сервер недоступен. Проверьте журнал среды."),Toast.LENGTH_LONG).show();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->{
                if(!isLocal(Uri.parse(request.getOrigin().toString())) || !java.util.Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)){request.deny();return;}
                if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                else {if(microphone!=null)microphone.deny();microphone=request;requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},44);}
            });}
            @Override public void onPermissionRequestCanceled(PermissionRequest request){if(microphone==request)microphone=null;}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(files!=null)files.onReceiveValue(null);files=callback;
                try{Intent intent=params.createIntent();intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,42);return true;}catch(ActivityNotFoundException e){files=null;return false;}
            }
            @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){new AlertDialog.Builder(WebActivity.this).setMessage(message).setPositiveButton("OK",(d,n)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){new AlertDialog.Builder(WebActivity.this).setMessage(message).setPositiveButton("OK",(d,n)->result.confirm()).setNegativeButton(L.t("Cancel","Отмена"),(d,n)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message result){
                WebView popup=new WebView(WebActivity.this);popup.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri uri=r.getUrl();if(isLocal(uri)){web.loadUrl(uri.toString());}else if("https".equals(uri.getScheme())||"http".equals(uri.getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException ignored){}}v.destroy();return true;}});((WebView.WebViewTransport)result.obj).setWebView(popup);result.sendToTarget();return true;
            }
        });
        if(state!=null)web.restoreState(state);else loadInitial();
    }
    private void loadInitial(){
        String base=Uri.parse(RuntimeService.url).buildUpon().path("/").build().toString();
        if(RuntimeService.paired){web.loadUrl(base);return;}
        io.execute(()->{try{
            Config config=new Config();config.mode="agent";config.base=base;config.username="opencode";config.password=RuntimeService.password;
            String code=new JSONObject(new Api().request(config,"/api/pair","POST",new JSONObject())).getString("code");
            runOnUiThread(()->{if(!isDestroyed())web.loadUrl(base+"auth/connect/"+code);});
        }catch(Exception error){runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,L.t("Could not connect to the local core","Не удалось подключиться к локальному ядру"),Toast.LENGTH_LONG).show();});}});
    }
    private boolean isLocal(Uri uri){return "http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost())&&uri.getPort()==Uri.parse(RuntimeService.url).getPort();}
    public final class Downloads {
        @JavascriptInterface public void save(String name,String mime,String data){if(data.length()>35_000_000){failed();return;}try{byte[] bytes=android.util.Base64.decode(data,android.util.Base64.DEFAULT);runOnUiThread(()->offerDownload(name,mime,bytes));}catch(Exception e){failed();}}
        @JavascriptInterface public void failed(){runOnUiThread(()->Toast.makeText(WebActivity.this,L.t("Could not save the file (25 MB maximum)","Не удалось сохранить файл (максимум 25 МБ)"),Toast.LENGTH_LONG).show());}
    }
    private void offerDownload(String name,String mime,byte[] bytes){
        if(isDestroyed())return;download=bytes;
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime==null||mime.isEmpty()?"application/octet-stream":mime).putExtra(Intent.EXTRA_TITLE,name.replace('/','_').replace('\\','_'));
        try{startActivityForResult(save,43);}catch(ActivityNotFoundException e){download=null;Toast.makeText(this,L.t("No app available to save documents","Нет приложения для сохранения документов"),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==44&&microphone!=null){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)microphone.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else microphone.deny();microphone=null;}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&files!=null){files.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));files=null;}if(request==43){byte[] bytes=download;download=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&bytes!=null){Uri target=data.getData();io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(target)){if(out!=null)out.write(bytes);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,L.t("Could not write the file","Не удалось записать файл"),Toast.LENGTH_LONG).show());}});}}}
    @Override protected void onSaveInstanceState(Bundle state){web.saveState(state);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){io.shutdown();if(microphone!=null)microphone.deny();if(files!=null)files.onReceiveValue(null);if(web!=null)web.destroy();super.onDestroy();}
}
