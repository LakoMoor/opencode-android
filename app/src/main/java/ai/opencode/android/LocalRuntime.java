package ai.opencode.android;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Runs the unchanged upstream CLI and its embedded UI inside a private PRoot filesystem. */
final class LocalRuntime {
    interface Progress { void update(String line); }
    private final Context context;
    final File root;
    LocalRuntime(Context context) { this.context=context; root=new File(context.getFilesDir(),"linux"); }
    void install(Progress progress) throws Exception {
        if(!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")) throw new IOException(L.t("The local core requires an ARM64 phone","Для локального ядра требуется телефон ARM64"));
        File marker=new File(root,".opencode-android-runtime-v1");
        if(marker.exists()) { ensureTools(); copy(context.getAssets().open("pty-bridge.py"),new File(root,"root/pty-bridge.py")); return; }
        if(context.getFilesDir().getUsableSpace()<1_500_000_000L) throw new IOException(L.t("At least 1.5 GB of free storage is required to install","Для установки нужно минимум 1,5 ГБ свободной памяти"));
        File installRoot=new File(context.getFilesDir(),"linux-installing");
        if(installRoot.exists()) deleteTree(installRoot);
        progress.update(L.t("Unpacking Ubuntu ARM64 (1/3)…","Распаковываем Ubuntu ARM64 (1/3)…"));
        installRoot.mkdirs();
        extract(context.getAssets().open("runtime/ubuntu-base-24.04.5-base-arm64.tgz"),installRoot);
        progress.update(L.t("Installing OpenCode 2.2.2 (2/3)…","Устанавливаем OpenCode 2.2.2 (2/3)…"));
        File stage=new File(context.getCacheDir(),"opencode-unpack"); stage.mkdirs();
        extract(context.getAssets().open("runtime/opencode-linux-arm64.tgz"),stage);
        File binary=new File(stage,"opencode");
        File bin=new File(installRoot,"usr/local/bin"); bin.mkdirs();
        copy(new FileInputStream(binary),new File(bin,"opencode")); new File(bin,"opencode").setExecutable(true,false); binary.delete();
        copy(context.getAssets().open("bootstrap.sh"),new File(installRoot,"root/bootstrap.sh"));
        new File(installRoot,"tmp").mkdirs(); new File(installRoot,"workspace").mkdirs();
        // resolv.conf can be a symlink to a systemd path absent in the container.
        File dns=new File(installRoot,"etc/resolv.conf"); Files.deleteIfExists(dns.toPath());
        write(dns,"nameserver 1.1.1.1\nnameserver 8.8.8.8\n");
        File libs=new File(context.getFilesDir(),"runtime-libs"); libs.mkdirs();
        copy(new FileInputStream(new File(context.getApplicationInfo().nativeLibraryDir,"libtalloc.so")),new File(libs,"libtalloc.so.2"));
        write(new File(installRoot,".opencode-android-runtime-v1"),"2.2.2\n");
        if(root.exists()) deleteTree(root);
        Files.move(installRoot.toPath(),root.toPath());
        progress.update(L.t("Installing Git, Python and tools (3/3)…","Устанавливаем Git, Python и инструменты (3/3)…"));
        ensureTools();
        copy(context.getAssets().open("pty-bridge.py"),new File(root,"root/pty-bridge.py"));
    }
    /** Dev tools ship pre-extracted inside the APK: unpacking takes seconds, no network, no dpkg under PRoot. */
    private void ensureTools() throws Exception {
        File marker=new File(root,"root/.opencode-android-tools-v3");
        if(marker.exists()) return;
        extract(context.getAssets().open("runtime/tools-overlay.tgz"),root);
        write(marker,"1\n");
    }
    Process start(String password,int port,Progress progress) throws Exception {
        File projects=new File(context.getFilesDir(),"projects"); projects.mkdirs();
        File temp=new File(context.getCacheDir(),"proot"); temp.mkdirs();
        String nativeDir=context.getApplicationInfo().nativeLibraryDir;
        List<String> command=new ArrayList<>(Arrays.asList(nativeDir+"/libproot.so","--link2symlink","--kill-on-exit","-0","-r",root.getAbsolutePath(),"-b","/dev","-b","/proc","-b","/sys","-b",projects.getAbsolutePath()+":/workspace","-w","/workspace","/usr/bin/env","-i","HOME=/root","USER=root","LOGNAME=root","LANG=C.UTF-8","TERM=xterm-256color","SHELL=/bin/bash","PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin","TMPDIR=/tmp","OPENCODE_PASSWORD="+password,"OPENCODE_ANDROID_PORT="+port,"/bin/sh","/root/bootstrap.sh"));
        ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("LD_LIBRARY_PATH",new File(context.getFilesDir(),"runtime-libs").getAbsolutePath()+":"+nativeDir);
        builder.environment().put("PROOT_LOADER",nativeDir+"/libproot-loader.so");
        builder.environment().put("PROOT_TMP_DIR",temp.getAbsolutePath());
        builder.environment().put("PROOT_NO_SECCOMP","1");
        builder.environment().put("TMPDIR",temp.getAbsolutePath());
        progress.update(L.t("Starting the local core…","Запускаем локальное ядро…"));
        return builder.start();
    }
    /** Interactive terminal: "opencode" starts the original TUI, anything else opens an interactive bash. */
    Process startTerminal(String command,int rows,int cols) throws Exception {
        File projects=new File(context.getFilesDir(),"projects"); projects.mkdirs();
        File temp=new File(context.getCacheDir(),"proot"); temp.mkdirs();
        String nativeDir=context.getApplicationInfo().nativeLibraryDir;
        String[] program="opencode".equals(command)?new String[]{"/usr/local/bin/opencode"}:new String[]{"/bin/bash","--noprofile","--norc","-i"};
        List<String> cmd=new ArrayList<>(Arrays.asList(nativeDir+"/libproot.so","--link2symlink","--kill-on-exit","-0","-r",root.getAbsolutePath(),"-b","/dev","-b","/proc","-b","/sys","-b",projects.getAbsolutePath()+":/workspace","-w","/workspace","/usr/bin/env","-i","HOME=/root","USER=root","LOGNAME=root","LANG=C.UTF-8","TERM=xterm-256color","COLORTERM=truecolor","PS1=\\[\\e[92m\\]\\w\\[\\e[0m\\] \\$ ","SHELL=/bin/bash","PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin","TMPDIR=/tmp","ROWS="+rows,"COLS="+cols,"/usr/bin/python3","/root/pty-bridge.py"));
        cmd.addAll(Arrays.asList(program));
        ProcessBuilder builder=new ProcessBuilder(cmd).redirectErrorStream(true);
        builder.environment().put("LD_LIBRARY_PATH",new File(context.getFilesDir(),"runtime-libs").getAbsolutePath()+":"+nativeDir);
        builder.environment().put("PROOT_LOADER",nativeDir+"/libproot-loader.so");
        builder.environment().put("PROOT_TMP_DIR",temp.getAbsolutePath());
        builder.environment().put("PROOT_NO_SECCOMP","1");
        builder.environment().put("TMPDIR",temp.getAbsolutePath());
        return builder.start();
    }
    void configure(String base,String key,String model) throws Exception {
        File directory=new File(root,"root/.config/opencode"); directory.mkdirs();
        File file=new File(directory,"opencode.json");
        JSONObject config=file.exists()?new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)):new JSONObject();
        JSONObject providers=config.optJSONObject("providers"); if(providers==null) providers=new JSONObject();
        JSONObject provider=new JSONObject().put("name","My OpenAI-compatible server").put("package","@opencode/ai/providers/openai-compatible").put("settings",new JSONObject().put("baseURL",base).put("apiKey",key)).put("models",new JSONObject().put(model,new JSONObject().put("name",model).put("capabilities",new JSONObject().put("tools",true).put("input",new JSONArray().put("text")).put("output",new JSONArray().put("text")))));
        providers.put("myserver",provider); config.put("providers",providers).put("model","myserver/"+model);
        write(file,config.toString(2));
        file.setReadable(false,false); file.setReadable(true,true); file.setWritable(false,false); file.setWritable(true,true);
    }
    private static void deleteTree(File directory) throws IOException {
        try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(directory.toPath())) {
            for(java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }
    static void write(File file,String content) throws IOException { copy(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),file); }
    static void copy(InputStream input,File target) throws IOException {
        File parent=target.getParentFile(); if(parent!=null) parent.mkdirs();
        try(InputStream in=input; OutputStream out=new FileOutputStream(target)) { byte[] buffer=new byte[65536]; int count; while((count=in.read(buffer))!=-1) out.write(buffer,0,count); }
    }
    static void extract(InputStream compressed,File directory) throws Exception {
        String prefix=directory.getCanonicalPath()+"/";
        ArrayList<TarArchiveEntry> links=new ArrayList<>();
        try(TarArchiveInputStream tar=new TarArchiveInputStream(new GZIPInputStream(compressed,65536))) {
            TarArchiveEntry entry;
            while((entry=tar.getNextEntry())!=null) {
                File target=new File(directory,entry.getName());
                if(!target.getCanonicalPath().startsWith(prefix)) { if(entry.getName().equals("./")) continue; throw new IOException("Unsafe archive path: "+entry.getName()); }
                if(entry.isSymbolicLink()||entry.isLink()) { links.add(entry); continue; }
                if(entry.isDirectory()) { target.mkdirs(); continue; }
                if(!entry.isFile()) continue;
                target.getParentFile().mkdirs();
                try(OutputStream out=new FileOutputStream(target)) { byte[] buffer=new byte[65536]; int n; while((n=tar.read(buffer))!=-1) out.write(buffer,0,n); }
                if((entry.getMode()&0111)!=0) target.setExecutable(true,false);
            }
        }
        // Install links last, so archive writes never traverse a guest symlink.
        for(TarArchiveEntry link:links) {
            File target=new File(directory,link.getName());
            if(!target.getParentFile().getCanonicalPath().startsWith(prefix) && !target.getParentFile().getCanonicalPath().equals(directory.getCanonicalPath())) throw new IOException("Unsafe link parent");
            target.getParentFile().mkdirs(); Files.deleteIfExists(target.toPath());
            if(link.isSymbolicLink()) Os.symlink(link.getLinkName(),target.getAbsolutePath());
            else {
                File source=new File(directory,link.getLinkName()); if(!source.getCanonicalPath().startsWith(prefix)) throw new IOException("Unsafe hardlink");
                // Android SELinux denies hardlink creation in app data on some devices.
                copy(new FileInputStream(source),target); if(source.canExecute()) target.setExecutable(true,false);
            }
        }
    }
}
