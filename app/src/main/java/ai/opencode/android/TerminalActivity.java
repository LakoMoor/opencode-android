package ai.opencode.android;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Real terminal on a PTY inside the local Linux: OpenCode TUI or a plain shell. */
public class TerminalActivity extends Activity {
    static final String EXTRA_COMMAND="command";
    private WebView web;
    private java.lang.Process process;
    private OutputStream stdin;
    private final ExecutorService io=Executors.newCachedThreadPool();
    private volatile boolean closed;
    private int rows=24,cols=80;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(16,18,17));
        web=new WebView(this);web.setBackgroundColor(Color.rgb(16,18,17));web.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        frame.addView(web);setContentView(frame);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(),"Term");
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}});
        web.loadUrl("file:///android_asset/terminal/index.html");
    }
    private void js(String script) { if(!closed&&web!=null) web.post(()->{if(!closed)web.evaluateJavascript(script,null);}); }
    private void begin() {
        String command=getIntent().getStringExtra(EXTRA_COMMAND);
        io.execute(()->{try{
            LocalRuntime runtime=new LocalRuntime(this);
            if(RuntimeService.running&&!RuntimeService.ready) throw new IOException(L.t("Please wait: the environment is still being prepared","Подождите: среда ещё готовится"));
            runtime.install(line->js("term.write("+org.json.JSONObject.quote("\u001b[90m"+line+"\u001b[0m\r\n")+")"));
            process=runtime.startTerminal(command,rows,cols);
            stdin=process.getOutputStream();
            try(InputStream out=process.getInputStream()) {
                byte[] buffer=new byte[16384];int n;
                while((n=out.read(buffer))!=-1) js("feed('"+Base64.encodeToString(buffer,0,n,Base64.NO_WRAP)+"')");
            }
            int code=process.waitFor();js("exited("+code+")");
        }catch(Exception e){js("term.write("+org.json.JSONObject.quote("\u001b[31m"+e.getMessage()+"\u001b[0m\r\n")+")");}});
    }
    public final class Bridge {
        @JavascriptInterface public void ready(int r,int c){rows=r;cols=c;begin();}
        @JavascriptInterface public void input(String b64){byte[] bytes=Base64.decode(b64,Base64.DEFAULT);io.execute(()->write(bytes));}
        @JavascriptInterface public void resize(int r,int c){rows=r;cols=c;io.execute(()->write(("\u0000R"+r+","+c+"\n").getBytes()));}
    }
    private synchronized void write(byte[] bytes){try{if(stdin!=null){stdin.write(bytes);stdin.flush();}}catch(IOException ignored){}}
    @Override public void onBackPressed(){
        new AlertDialog.Builder(this).setMessage(L.t("Close the terminal? The process running in it will be stopped.","Закрыть терминал? Запущенный в нём процесс будет остановлен.")).setPositiveButton(L.t("Close","Закрыть"),(d,n)->finish()).setNegativeButton(L.t("Stay","Остаться"),null).show();
    }
    @Override protected void onDestroy(){closed=true;if(process!=null)process.destroy();io.shutdownNow();if(web!=null)web.destroy();super.onDestroy();}
}
