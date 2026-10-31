package ai.opencode.android;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Catalog of skills, commands, agents, MCP servers and plugins; installs them into OpenCode's config inside the guest. */
final class Hub {
    static final String REMOTE="https://raw.githubusercontent.com/"+Updater.REPO+"/main/app/src/main/assets/hub/catalog.json";
    private final Context context; private final LocalRuntime runtime;
    Hub(Context context) { this.context=context; runtime=new LocalRuntime(context); }

    boolean ready() { return new File(runtime.root,".opencode-android-runtime-v1").exists(); }
    /** Built-in catalog, overridden by the last downloaded remote copy (matched by type+id). */
    List<JSONObject> items() throws Exception {
        LinkedHashMap<String,JSONObject> map=new LinkedHashMap<>();
        merge(map,new String(readAll(context.getAssets().open("hub/catalog.json")),StandardCharsets.UTF_8));
        File cache=new File(context.getFilesDir(),"hub-catalog.json");
        if(cache.exists()) try { merge(map,new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8)); } catch(Exception ignored) {}
        return new ArrayList<>(map.values());
    }
    private static void merge(Map<String,JSONObject> map,String json) throws JSONException {
        JSONArray a=new JSONObject(json).getJSONArray("items");
        for(int i=0;i<a.length();i++) { JSONObject o=a.getJSONObject(i); map.put(o.getString("type")+":"+o.getString("id"),o); }
    }
    /** Pulls the newest catalog from GitHub so new entries appear without an APK update. */
    int refresh() throws Exception {
        String body=Updater.get(REMOTE); merge(new LinkedHashMap<>(),body);
        LocalRuntime.write(new File(context.getFilesDir(),"hub-catalog.json"),body);
        return items().size();
    }
    private File dirFor(String type) { return new File(runtime.configDir(),type.equals("skill")?"skills":type.equals("command")?"command":"agent"); }
    private File configFile() { return new File(runtime.configDir(),"opencode.json"); }
    private JSONObject config() throws Exception { File f=configFile(); return f.exists()?new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8)):new JSONObject(); }
    private void save(JSONObject c) throws Exception { LocalRuntime.write(configFile(),c.toString(2)); }

    boolean installed(JSONObject item) {
        try {
            String type=item.getString("type"),id=item.getString("id");
            switch(type) {
                case "skill": return new File(dirFor(type),id+"/SKILL.md").exists();
                case "command": case "agent": return new File(dirFor(type),id+".md").exists();
                case "plugin": { JSONArray a=config().optJSONArray("plugin"); for(int i=0;a!=null&&i<a.length();i++) if(a.getString(i).equals(item.getString("package"))) return true; return false; }
                case "mcp": { JSONObject m=config().optJSONObject("mcp"); return m!=null&&m.has(id); }
            }
        } catch(Exception ignored) {}
        return false;
    }
    void install(JSONObject item) throws Exception {
        if(!ready()) throw new IOException(L.t("Start OpenCode once first, so the local environment is created.","Сначала один раз запустите OpenCode, чтобы создалась локальная среда."));
        String type=item.getString("type"),id=item.getString("id");
        switch(type) {
            case "skill": { JSONObject files=item.getJSONObject("files"); for(Iterator<String> k=files.keys();k.hasNext();) { String name=k.next(); LocalRuntime.write(new File(dirFor(type),id+"/"+safe(name)),files.getString(name)); } break; }
            case "command": case "agent": { JSONObject files=item.getJSONObject("files"); for(Iterator<String> k=files.keys();k.hasNext();) { String name=k.next(); LocalRuntime.write(new File(dirFor(type),safe(name)),files.getString(name)); } break; }
            case "plugin": { JSONObject c=config(); JSONArray a=c.optJSONArray("plugin"); if(a==null) a=new JSONArray(); a.put(item.getString("package")); c.put("plugin",a); save(c); break; }
            case "mcp": { JSONObject c=config(); JSONObject m=c.optJSONObject("mcp"); if(m==null) m=new JSONObject(); m.put(id,item.getJSONObject("config")); c.put("mcp",m); save(c); break; }
        }
    }
    void remove(JSONObject item) throws Exception {
        String type=item.getString("type"),id=item.getString("id");
        switch(type) {
            case "skill": deleteTree(new File(dirFor(type),id)); break;
            case "command": case "agent": new File(dirFor(type),id+".md").delete(); break;
            case "plugin": { JSONObject c=config(); JSONArray a=c.optJSONArray("plugin"),n=new JSONArray(); for(int i=0;a!=null&&i<a.length();i++) if(!a.getString(i).equals(item.getString("package"))) n.put(a.getString(i)); c.put("plugin",n); save(c); break; }
            case "mcp": { JSONObject c=config(); JSONObject m=c.optJSONObject("mcp"); if(m!=null) { m.remove(id); c.put("mcp",m); save(c); } break; }
        }
    }
    /** Installs a SKILL.md from a GitHub (blob or raw) URL; returns the skill name. */
    String installSkillFromUrl(String url) throws Exception {
        if(!ready()) throw new IOException(L.t("Start OpenCode once first, so the local environment is created.","Сначала один раз запустите OpenCode, чтобы создалась локальная среда."));
        url=url.trim().replace("https://github.com/","https://raw.githubusercontent.com/").replace("/blob/","/");
        if(!url.startsWith("https://raw.githubusercontent.com/")) throw new IOException(L.t("Use a link to a SKILL.md file on GitHub","Укажите ссылку на файл SKILL.md на GitHub"));
        if(!url.endsWith(".md")) url=url.replaceAll("/+$","")+"/SKILL.md";
        String body=Updater.get(url);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^name:\\s*([a-z0-9][a-z0-9-]{0,63})\\s*$").matcher(body);
        if(!body.startsWith("---")||!m.find()) throw new IOException(L.t("The file has no valid SKILL.md header (name:)","В файле нет корректного заголовка SKILL.md (name:)"));
        LocalRuntime.write(new File(dirFor("skill"),m.group(1)+"/SKILL.md"),body); return m.group(1);
    }
    private static String safe(String name) throws IOException { if(name.contains("/")||name.contains("..")) throw new IOException("bad file name"); return name; }
    private static void deleteTree(File f) { File[] c=f.listFiles(); if(c!=null) for(File x:c) deleteTree(x); f.delete(); }
    private static byte[] readAll(InputStream in) throws IOException { try(InputStream i=in; ByteArrayOutputStream o=new ByteArrayOutputStream()) { byte[] b=new byte[8192]; int n; while((n=i.read(b))>0) o.write(b,0,n); return o.toByteArray(); } }
}
