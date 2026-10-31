package ai.opencode.android;

import android.content.Context;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Follows the local OpenCode server's event stream and turns it into dynamic-island states and notifications. */
final class EventWatcher {
    private final Context context; private final int port; private final String password;
    private volatile boolean stopped; private volatile HttpURLConnection connection;
    private static final String[] PATHS={"/event","/api/event","/global/event"};
    private int path; private String lastTool="";
    EventWatcher(Context context,int port,String password) { this.context=context.getApplicationContext(); this.port=port; this.password=password; }
    void start() { Thread t=new Thread(this::loop,"opencode-events"); t.setDaemon(true); t.start(); }
    void stop() { stopped=true; HttpURLConnection c=connection; if(c!=null) c.disconnect(); }
    private void loop() {
        while(!stopped) {
            try {
                HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+PATHS[path%PATHS.length]).openConnection(); connection=c;
                c.setRequestProperty("Authorization","Basic "+Base64.encodeToString(("opencode:"+password).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));
                c.setRequestProperty("Accept","text/event-stream"); c.setConnectTimeout(3000); c.setReadTimeout(0);
                int code=c.getResponseCode(); String kind=String.valueOf(c.getContentType());
                android.util.Log.i("OpenCodeEvents",PATHS[path%PATHS.length]+" -> "+code+" "+kind);
                if(code!=200||!kind.contains("event-stream")) { path++; throw new IOException("HTTP "+code+" "+kind); }
                try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))) {
                    String line; while(!stopped&&(line=r.readLine())!=null) if(line.startsWith("data:")) handle(line.substring(5).trim());
                }
            } catch(Exception e) { android.util.Log.i("OpenCodeEvents",String.valueOf(e)); }
            try { Thread.sleep(2000); } catch(InterruptedException e) { return; }
        }
    }
    private void handle(String data) {
        try {
            JSONObject json=new JSONObject(data); String type=json.optString("type"); JSONObject p=json.optJSONObject("data"); if(p==null) p=json.optJSONObject("properties"); if(p==null) p=new JSONObject();
            android.util.Log.i("OpenCodeEvents","event "+type+" "+(data.length()>300?data.substring(0,300):data));
            switch(type) {
                case "session.status": {
                    JSONObject st=p.optJSONObject("status"); String s=st==null?p.optString("status"):st.optString("type");
                    if("busy".equals(s)||"retry".equals(s)) Island.notifyEvent(context,Island.BUSY,L.t("OpenCode is working","OpenCode работает"),lastTool);
                    break; }
                case "session.execution.started": Island.notifyEvent(context,Island.BUSY,L.t("OpenCode is working","OpenCode работает"),lastTool); break;
                case "session.tool.called": { String t=p.optString("tool",p.optString("name")); if(!t.isEmpty()) lastTool=t; Island.notifyEvent(context,Island.BUSY,L.t("OpenCode is working","OpenCode работает"),lastTool); break; }
                case "session.execution.succeeded": lastTool=""; Island.notifyEvent(context,Island.DONE,L.t("Task finished","Задача завершена"),L.t("OpenCode is waiting for your next message","OpenCode ждёт следующего сообщения")); break;
                case "session.execution.failed": { Object e=p.opt("error"); String m=e instanceof JSONObject?((JSONObject)e).optString("message",((JSONObject)e).optString("name")):String.valueOf(e==null?"":e); lastTool=""; Island.notifyEvent(context,Island.ERROR,L.t("Task failed","Задача не выполнена"),m); break; }
                case "session.execution.interrupted": lastTool=""; Island.notifyEvent(context,Island.INFO,L.t("Stopped","Остановлено"),""); break;
                case "session.idle": lastTool=""; Island.notifyEvent(context,Island.DONE,L.t("Task finished","Задача завершена"),L.t("OpenCode is waiting for your next message","OpenCode ждёт следующего сообщения")); break;
                case "session.error": { JSONObject err=p.optJSONObject("error"); String m=err==null?"":err.optJSONObject("data")!=null?err.optJSONObject("data").optString("message"):err.optString("name"); Island.notifyEvent(context,Island.ERROR,L.t("Session error","Ошибка сессии"),m); break; }
                case "permission.asked": case "permission.updated": case "permission.request": { String t=p.optString("title",p.optString("permission",p.optString("type"))); Island.notifyEvent(context,Island.ASK,L.t("Permission needed","Нужно разрешение"),t); break; }
                case "question.asked": Island.notifyEvent(context,Island.ASK,L.t("OpenCode has a question","У OpenCode вопрос"),""); break;
                case "message.part.updated": {
                    JSONObject part=p.optJSONObject("part"); if(part!=null&&"tool".equals(part.optString("type"))) { JSONObject state=part.optJSONObject("state"); if(state!=null&&"running".equals(state.optString("status"))) { lastTool=part.optString("tool"); Island.notifyEvent(context,Island.BUSY,L.t("OpenCode is working","OpenCode работает"),lastTool); } }
                    break; }
                default: break;
            }
        } catch(Exception ignored) {}
    }
}
