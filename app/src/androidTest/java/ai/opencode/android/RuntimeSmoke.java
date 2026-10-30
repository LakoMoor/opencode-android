package ai.opencode.android;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Runs real device integration checks against the unmodified bundled upstream core. */
public class RuntimeSmoke extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        File providerFile=new File(getTargetContext().getFilesDir(),"linux/root/.config/opencode/opencode.json");
        byte[] previous=null;
        MockModel mock=null;
        int code=Activity.RESULT_CANCELED;
        try {
            new LocalRuntime(getTargetContext()).install(line->android.util.Log.i("RuntimeSmoke",line));
            mock=new MockModel();
            if(providerFile.exists())previous=Files.readAllBytes(providerFile.toPath());
            new LocalRuntime(getTargetContext()).configure("http://127.0.0.1:"+mock.socket.getLocalPort()+"/v1","smoke-key","android-smoke-model");
            startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getTargetContext().startForegroundService(new Intent(getTargetContext(),RuntimeService.class));
            long deadline=System.currentTimeMillis()+15*60*1000L;
            while(!RuntimeService.ready&&System.currentTimeMillis()<deadline){Thread.sleep(1000);if(!RuntimeService.running)throw new Exception(RuntimeService.logs());}
            require(RuntimeService.ready,"Core readiness timed out: "+RuntimeService.logs());
            Config c=new Config();c.mode="agent";c.base="http://127.0.0.1:"+android.net.Uri.parse(RuntimeService.url).getPort();c.username="opencode";c.password=RuntimeService.password;
            Api api=new Api();
            require(new JSONObject(api.request(c,"/api/info","GET",null)).getString("version").equals("2.2.2"),"Wrong upstream version");
            String html=api.request(c,"/","GET",null);require(html.contains("<html")&&html.contains("script"),"Original web UI missing");
            JSONObject input=new JSONObject().put("command","printf android-runtime-ok > /workspace/.android-smoke; git --version; python3 --version; cat /workspace/.android-smoke");
            JSONObject shell=new JSONObject(api.request(c,"/api/shell","POST",input)).getJSONObject("data");
            String id=shell.getString("id");
            for(int i=0;i<120;i++){shell=new JSONObject(api.request(c,"/api/shell/"+id,"GET",null)).getJSONObject("data");if(!shell.getString("status").equals("running"))break;Thread.sleep(500);}
            require(shell.optInt("exit",-1)==0,"Local shell failed: "+shell);
            String output=new JSONObject(api.request(c,"/api/shell/"+id+"/output","GET",null)).getJSONObject("data").getString("output");
            require(output.contains("android-runtime-ok")&&output.contains("git version")&&output.contains("Python"),"Development tools missing: "+output);
            String file=api.request(c,"/api/fs/read/.android-smoke","GET",null);require(file.equals("android-runtime-ok"),"File read mismatch");
            JSONObject terminal=new JSONObject(api.request(c,"/api/pty","POST",new JSONObject().put("command","/bin/bash").put("args",new JSONArray().put("-lc").put("printf terminal-ok; sleep 2")))).getJSONObject("data");
            require(terminal.getInt("pid")>0,"PTY did not start");
            api.request(c,"/api/pty/"+terminal.getString("id"),"DELETE",null);
            api.request(c,"/api/shell/"+id,"DELETE",null);
            JSONObject provider=new JSONObject(api.request(c,"/api/provider/myserver","GET",null)).getJSONObject("data");
            require(provider.getString("package").equals("@opencode/ai/providers/openai-compatible"),"Custom provider not applied");
            JSONObject session=new JSONObject(api.request(c,"/api/session","POST",new JSONObject().put("title","Android integration smoke").put("model",new JSONObject().put("providerID","myserver").put("id","android-smoke-model")))).getJSONObject("data");
            String sessionId=session.getString("id");
            api.request(c,"/api/session/"+sessionId+"/prompt","POST",new JSONObject().put("text","Reply with android-model-ok. Do not use tools."));
            String context="";
            for(int i=0;i<120;i++) {
                context=api.request(c,"/api/session/"+sessionId+"/context","GET",null);
                if(context.contains("android-model-ok"))break;
                Thread.sleep(500);
            }
            require(context.contains("android-model-ok"),"Compatible model failed: "+context);
            require(mock.authorized,"Custom model Bearer authorization missing");
            result.putString("stream","PASS: upstream 2.2.2; original UI; core boot; local shell; Git; Python; filesystem; PTY; custom provider; OpenAI-compatible streamed inference\n");code=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n"+RuntimeService.logs());code=Activity.RESULT_CANCELED;}
        finally {
            getTargetContext().stopService(new Intent(getTargetContext(),RuntimeService.class));
            try{if(previous==null)Files.deleteIfExists(providerFile.toPath());else Files.write(providerFile.toPath(),previous);}catch(Exception ignored){}
            if(mock!=null)try{mock.socket.close();}catch(Exception ignored){}
        }
        finish(code,result);
    }
    private static class MockModel {
        final ServerSocket socket=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
        volatile boolean authorized;
        MockModel() throws Exception {
            Thread thread=new Thread(()->{
                while(!socket.isClosed())try(Socket client=socket.accept()) {
                    client.setSoTimeout(10000);
                    InputStream input=client.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int next;
                    while((next=input.read())!=-1){header.write(next);if(header.toString("UTF-8").endsWith("\r\n\r\n"))break;}
                    String headers=header.toString("UTF-8");int count=0;
                    for(String line:headers.split("\r\n"))if(line.toLowerCase().startsWith("content-length:"))count=Integer.parseInt(line.substring(15).trim());
                    byte[] body=new byte[count];int read=0;while(read<count){int n=input.read(body,read,count-read);if(n<0)break;read+=n;}
                    if(headers.toLowerCase().contains("authorization: bearer smoke-key"))authorized=true;
                    String response="data: {\"id\":\"chatcmpl-smoke\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"android-smoke-model\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"android-model-ok\"},\"finish_reason\":null}]}\n\n"+
                        "data: {\"id\":\"chatcmpl-smoke\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"android-smoke-model\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14}}\n\ndata: [DONE]\n\n";
                    byte[] bytes=response.getBytes(StandardCharsets.UTF_8);OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.flush();
                }catch(Exception ignored){}
            });thread.setDaemon(true);thread.start();
        }
    }
    private void require(boolean condition,String message)throws Exception{if(!condition)throw new Exception(message);}
}
