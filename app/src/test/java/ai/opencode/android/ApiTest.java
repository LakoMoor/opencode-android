package ai.opencode.android;

import org.junit.Test;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ApiTest {
    @Test public void acceptsBasePathsButRejectsCredentialsAndWrongEndpoints() {
        Config c=new Config(); c.base="http://localhost:1234/custom/v1///";
        assertEquals("http://localhost:1234/custom/v1",c.normalizedBase());
        for(String bad:new String[]{"file:///tmp", "https://key@example.org/v1", "http://host/v1?api_key=secret", "http://host/v1/chat/completions", "host:1234"}) {
            c.base=bad;
            try { c.normalizedBase(); fail(bad); } catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void streamsUtf8AndSkipsUsageEvents() throws Exception {
        String response=": keepalive\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"Привет \"}}]}\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"🌍\"}}]}\n\ndata: {\"choices\":[],\"usage\":{}}\n\ndata: [DONE]\n\n";
        try(Mock server=new Mock(200,"text/event-stream",response)) {
            Config c=config(server); c.key="test-key";
            StringBuilder out=new StringBuilder(); new Api().chat(c,new JSONArray().put(new JSONObject().put("role","user").put("content","hi")),out::append);
            assertEquals("Привет 🌍",out.toString());
            String request=server.request.get(2,TimeUnit.SECONDS);
            assertTrue(request.startsWith("POST /v1/chat/completions "));
            assertTrue(request.contains("Bearer test-key"));
            assertTrue(new JSONObject(request.substring(request.indexOf("\r\n\r\n")+4)).getBoolean("stream"));
        }
    }
    @Test public void supportsNonStreamingJsonFallback() throws Exception {
        try(Mock server=new Mock(200,"application/json","{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}")) {
            StringBuilder out=new StringBuilder(); new Api().chat(config(server),new JSONArray(),out::append); assertEquals("hello",out.toString());
        }
    }
    @Test public void authErrorsDoNotLeakServerBody() throws Exception {
        try(Mock server=new Mock(401,"application/json","secret-key-from-upstream")) {
            try { new Api().request(config(server),"/models","GET",null); fail(); }
            catch(IOException e) { assertTrue(e.getMessage().contains("401")); assertFalse(e.getMessage().contains("secret-key")); }
        }
    }
    @Test public void cancelledRequestNeverStarts() throws Exception {
        Api api=new Api(); api.cancel();
        try { api.request(new Config(),"/models","GET",null); fail(); } catch(IOException e) { assertTrue(api.cancelled()); }
    }
    private Config config(Mock server) { Config c=new Config(); c.base="http://127.0.0.1:"+server.socket.getLocalPort()+"/v1"; c.model="local-model"; return c; }
    private static final class Mock implements AutoCloseable {
        final ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
        final CompletableFuture<String> request=new CompletableFuture<>();
        Mock(int status,String type,String body) throws Exception {
            Thread t=new Thread(() -> {
                try(Socket peer=socket.accept()) {
                    peer.setSoTimeout(3000); InputStream input=peer.getInputStream(); ByteArrayOutputStream headers=new ByteArrayOutputStream();
                    int b; while((b=input.read())!=-1) { headers.write(b); if(headers.toString("UTF-8").endsWith("\r\n\r\n")) break; }
                    String h=headers.toString("UTF-8"); int size=0;
                    for(String line:h.split("\r\n")) if(line.toLowerCase().startsWith("content-length:")) size=Integer.parseInt(line.substring(15).trim());
                    request.complete(h+new String(input.readNBytes(size),StandardCharsets.UTF_8));
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    OutputStream out=peer.getOutputStream(); out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); out.write(bytes); out.flush();
                } catch(Exception e) { request.completeExceptionally(e); }
            }); t.setDaemon(true); t.start();
        }
        public void close() throws Exception { socket.close(); }
    }
}
