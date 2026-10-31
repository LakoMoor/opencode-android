package ai.opencode.android;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.concurrent.*;
import org.json.JSONObject;

public class RuntimeService extends Service {
    static volatile String state=L.t("Not running","Не запущен"), url="", password="";
    static volatile boolean running=false, ready=false, paired=false;
    static final int LLAMA_PORT=8081;
    static volatile boolean llamaRunning=false, llamaReady=false, openWhenReady=false;
    static volatile String llamaModel="", llamaState="";
    private java.lang.Process llama;
    private static final StringBuilder log=new StringBuilder();
    private java.lang.Process process;
    private EventWatcher events;
    private PowerManager.WakeLock wakeLock;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    static synchronized String logs() { return log.toString(); }
    private static synchronized void append(String line) { if(!password.isEmpty())line=line.replace(password,"[local credential]"); android.util.Log.i("OpenCodeRuntime",line); if(log.length()>40000) log.delete(0,20000); log.append(line).append('\n'); }
    @Override public void onCreate() {
        super.onCreate();
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OpenCode:runtime");
        wakeLock.acquire(10*60*60*1000L);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("runtime",L.t("Local OpenCode","Локальный OpenCode"),NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,RuntimeService.class).setAction("stop");
        PendingIntent action=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"runtime").setSmallIcon(ai.opencode.android.R.drawable.ic_launcher).setContentTitle(L.t("OpenCode is running on your phone","OpenCode работает на телефоне")).setContentText(L.t("Local projects, tools and terminal","Локальные проекты, инструменты и терминал")).setContentIntent(content).setOngoing(true).addAction(new Notification.Action.Builder(null,L.t("Stop","Остановить"),action).build()).build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(intent!=null&&"llama-stop".equals(intent.getAction())) { stopLlama(); if(!running) stopSelf(); return START_NOT_STICKY; }
        if(intent!=null&&"llama-start".equals(intent.getAction())) { startLlama(intent.getStringExtra("model"),intent.getIntExtra("ctx",8192)); return START_NOT_STICKY; }
        if(intent!=null&&"core-restart".equals(intent.getAction())) { if(running&&process!=null) { restart=true; process.destroy(); } else if(!running) startCore(); return START_NOT_STICKY; }
        if(running) return START_NOT_STICKY;
        startCore(); return START_NOT_STICKY;
    }
    private volatile boolean restart;
    private void startCore() {
        running=true; ready=false; stopping=false; url=""; paired=false;
        worker.execute(() -> {
            try {
                LocalRuntime runtime=new LocalRuntime(this); runtime.install(this::progress);
                if(getSharedPreferences("runtime",0).getBoolean("applyProvider",false)) {
                    JSONObject provider=new JSONObject(new Vault(this).load());
                    runtime.configure(provider.getString("base"),provider.optString("key"),provider.getString("model"));
                    getSharedPreferences("runtime",0).edit().putBoolean("applyProvider",false).commit();
                }
                int port; try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { port=socket.getLocalPort(); }
                byte[] secret=new byte[32]; new SecureRandom().nextBytes(secret); password=android.util.Base64.encodeToString(secret,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);
                process=runtime.start(password,port,this::progress); events=new EventWatcher(this,port,password); events.start();
                String base="http://127.0.0.1:"+port;
                Thread probe=new Thread(() -> probe(base)); probe.setDaemon(true); probe.start();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) { String line; while((line=reader.readLine())!=null) append(line); }
                int exit=process.waitFor();
                if(!stopping&&!restart) progress(L.t("The core exited (","Ядро завершилось (")+exit+L.t("). Open the log for details.","). Откройте журнал для диагностики."));
            } catch(Exception e) { if(!stopping) progress(L.t("Local environment error: ","Ошибка локальной среды: ")+e.getMessage()); }
            finally { running=false; ready=false; url=""; if(events!=null) events.stop(); if(restart&&!stopping) { restart=false; startCore(); } else if(!llamaRunning) stopSelf(); }
        });
    }
    private void startLlama(String model,int ctx) {
        if(model==null||llamaRunning) return;
        llamaRunning=true; llamaReady=false; llamaModel=model; llamaState=L.t("Loading the model…","Загружаем модель…");
        new Thread(() -> {
            try {
                LocalRuntime runtime=new LocalRuntime(this); runtime.install(s -> llamaState=s);
                llamaState=L.t("Loading the model…","Загружаем модель…");
                llama=runtime.startLlama(model,LLAMA_PORT,ctx);
                Thread probe=new Thread(() -> { while(llamaRunning&&!llamaReady) { try { java.net.HttpURLConnection c=(java.net.HttpURLConnection)new URL("http://127.0.0.1:"+LLAMA_PORT+"/health").openConnection(); c.setConnectTimeout(1000); int code=c.getResponseCode(); c.disconnect(); if(code==200) { runtime.configure("local","Local model (llama.cpp)","http://127.0.0.1:"+LLAMA_PORT+"/v1","local","local"); llamaReady=true; llamaState=L.t("Model is running locally","Модель работает локально"); Island.notifyEvent(this,Island.INFO,L.t("Local model ready","Локальная модель готова"),model); return; } } catch(Exception ignored) {} try { Thread.sleep(1000); } catch(InterruptedException e) { return; } } });
                probe.setDaemon(true); probe.start();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(llama.getInputStream()))) { String line; while((line=reader.readLine())!=null) append("[llama] "+line); }
                int exit=llama.waitFor(); if(llamaRunning) llamaState=L.t("The model server exited (","Сервер модели завершился (")+exit+")";
            } catch(Exception e) { llamaState=L.t("Model error: ","Ошибка модели: ")+e.getMessage(); }
            finally { llamaRunning=false; llamaReady=false; if(!running) stopSelf(); }
        }).start();
    }
    private void stopLlama() { llamaRunning=false; llamaReady=false; if(llama!=null) llama.destroy(); llamaState=""; }
    private void progress(String line) { state=line; append(line); }
    private void probe(String base) {
        while(running&&!stopping&&!ready) {
            try {
                Config c=new Config(); c.mode="agent"; c.base=base; c.username="opencode"; c.password=password;
                String info=new Api().request(c,"/api/info","GET",null);
                new JSONObject(info).getString("version");
                String pairing=new Api().request(c,"/api/pair","POST",new JSONObject());
                url=base+"/auth/connect/"+new JSONObject(pairing).getString("code"); ready=true; state=L.t("OpenCode 2.2.2 · running locally on your phone","OpenCode 2.2.2 · локально на телефоне"); return;
            } catch(Exception ignored) { try { Thread.sleep(1000); } catch(InterruptedException e) { return; } }
        }
    }
    @Override public void onDestroy() { stopping=true; if(events!=null) events.stop(); Island.hide(); stopLlama(); running=false; ready=false; url=""; if(process!=null) process.destroy(); worker.shutdownNow(); if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release(); stopForeground(true); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
