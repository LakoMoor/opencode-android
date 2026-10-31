package ai.opencode.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Local models: curated picks and Hugging Face search for GGUF files, downloads, and a llama.cpp server running inside the app. */
public class ModelsActivity extends Activity {
    private static final String[][] PICKS={
        // repo, file, title, note (en), note (ru), size MB, context
        {"unsloth/Qwen3-4B-Instruct-2507-GGUF","Qwen3-4B-Instruct-2507-Q4_K_M.gguf","Qwen3 4B Instruct","Best all-round balance for phones; good at tool calls","Лучший баланс для телефона, хорошо вызывает инструменты","2381","8192"},
        {"Qwen/Qwen2.5-Coder-3B-Instruct-GGUF","qwen2.5-coder-3b-instruct-q4_k_m.gguf","Qwen2.5 Coder 3B","Code-focused, fast on most phones","Заточена под код, быстрая","2007","8192"},
        {"Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF","qwen2.5-coder-1.5b-instruct-q4_k_m.gguf","Qwen2.5 Coder 1.5B","Tiny and very fast, for simple edits","Компактная и очень быстрая, для простых правок","1065","8192"},
        {"unsloth/Qwen3-1.7B-GGUF","Qwen3-1.7B-Q4_K_M.gguf","Qwen3 1.7B","Small general model with reasoning","Небольшая универсальная модель с рассуждением","1056","8192"},
        {"bartowski/Llama-3.2-3B-Instruct-GGUF","Llama-3.2-3B-Instruct-Q4_K_M.gguf","Llama 3.2 3B Instruct","Meta's compact chat model","Компактная чат-модель Meta","1925","8192"},
        {"unsloth/gemma-3-4b-it-GGUF","gemma-3-4b-it-Q4_K_M.gguf","Gemma 3 4B","Google's model, strong multilingual","Модель Google, сильная в разных языках","2374","8192"},
        {"Qwen/Qwen2.5-Coder-7B-Instruct-GGUF","qwen2.5-coder-7b-instruct-q4_k_m.gguf","Qwen2.5 Coder 7B","Best quality here; needs 12 GB RAM, slower","Лучшее качество; нужно 12 ГБ ОЗУ, медленнее","4466","4096"}};
    // Download state outlives the activity so leaving the screen does not cancel a download.
    private static volatile String dlName="", dlError=""; private static volatile long dlDone, dlTotal; private static volatile boolean dlActive;
    private Ui ui; private LinearLayout runBox, mine, picks, results; private TextView note;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean active; private File dir;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); ui=new Ui(this); dir=new LocalRuntime(this).modelsDir();
        LinearLayout c=ui.screen("models");
        c.addView(ui.text(L.t("Run models fully on the phone with the bundled llama.cpp. Downloaded from Hugging Face once, then they work offline.","Запускайте модели прямо на телефоне через встроенный llama.cpp. Один раз скачиваются с Hugging Face и затем работают офлайн."),13,Ui.MUTED));
        runBox=ui.column();c.addView(runBox);
        note=ui.text("",13,Ui.ACCENT);note.setPadding(0,ui.dp(8),0,0);c.addView(note);
        c.addView(ui.section(L.t("on this phone","на телефоне")));mine=ui.column();c.addView(mine);
        c.addView(ui.section(L.t("recommended","рекомендуем")));picks=ui.column();c.addView(picks);
        c.addView(ui.section(L.t("search hugging face","поиск на hugging face")));
        EditText q=new EditText(this);q.setHint(L.t("e.g. coder, llama, mistral","например coder, llama, mistral"));q.setSingleLine();q.setTextColor(Ui.TEXT);q.setHintTextColor(Ui.DIM);q.setTypeface(android.graphics.Typeface.MONOSPACE);q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setOnEditorActionListener((v,a,e)->{search(q.getText().toString());return true;});c.addView(q);
        results=ui.column();c.addView(results);
        c.addView(ui.text(L.t("The model list comes from huggingface.co. Quantized GGUF files only; Q4_K_M is the usual sweet spot for phones.","Список берётся с huggingface.co. Только квантованные GGUF; Q4_K_M — обычно лучший выбор для телефона."),12,Ui.DIM));
        drawPicks();
    }
    @Override protected void onResume() { super.onResume(); active=true; main.post(tick); }
    @Override protected void onPause() { active=false; super.onPause(); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    private final Runnable tick=new Runnable(){ public void run(){ if(!active) return; drawRun(); drawMine(); main.postDelayed(this,1000); } };

    private String mb(long bytes) { return bytes>=1L<<30?String.format(Locale.US,"%.1f GB",bytes/1073741824.0):(bytes>>20)+" MB"; }
    private void drawRun() {
        runBox.removeAllViews();
        if(RuntimeService.llamaRunning) {
            LinearLayout card=ui.column();card.setBackground(ui.shape(Ui.PANEL,RuntimeService.llamaReady?Ui.GREEN:Ui.ACCENT,12));card.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(14));card.setLayoutParams(ui.lp(-1,-2,14));
            card.addView(ui.mono((RuntimeService.llamaReady?"● ":"○ ")+RuntimeService.llamaModel,13,RuntimeService.llamaReady?Ui.GREEN:Ui.ACCENT));
            TextView s=ui.text(RuntimeService.llamaState,13,Ui.MUTED);s.setPadding(0,ui.dp(4),0,0);card.addView(s);
            if(RuntimeService.llamaReady) card.addView(ui.button(L.t("Use in OpenCode","Использовать в OpenCode"),true,this::useInOpenCode));
            card.addView(ui.button(L.t("Stop model","Остановить модель"),false,()->startService(new Intent(this,RuntimeService.class).setAction("llama-stop"))));
            runBox.addView(card);
        } else if(!RuntimeService.llamaState.isEmpty()) { TextView s=ui.text(RuntimeService.llamaState,13,Ui.RED);s.setPadding(0,ui.dp(10),0,0);runBox.addView(s); }
        if(dlActive||!dlError.isEmpty()) {
            LinearLayout card=ui.column();card.setBackground(ui.shape(Ui.PANEL,Ui.LINE,12));card.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(14));card.setLayoutParams(ui.lp(-1,-2,10));
            if(dlActive) { card.addView(ui.mono(L.t("downloading ","загрузка ")+dlName,13,Ui.TEXT)); ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(1000);p.setProgress(dlTotal>0?(int)(dlDone*1000/dlTotal):0);p.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));card.addView(p,ui.lp(-1,ui.dp(6),10)); card.addView(ui.text(mb(dlDone)+" / "+mb(dlTotal),12,Ui.MUTED)); }
            else card.addView(ui.text(dlError,13,Ui.RED));
            runBox.addView(card);
        }
    }
    private void drawMine() {
        File[] files=dir.listFiles((d,n)->n.endsWith(".gguf")); mine.removeAllViews();
        if(files==null||files.length==0) { mine.addView(ui.text(L.t("No models yet. Pick one below.","Моделей пока нет. Выберите ниже."),13,Ui.DIM)); return; }
        Arrays.sort(files);
        for(File f:files) {
            boolean on=RuntimeService.llamaRunning&&f.getName().equals(RuntimeService.llamaModel);
            mine.addView(ui.item(f.getName(),mb(f.length()),on?L.t("running","запущена"):L.t("run","запустить"),on?Ui.GREEN:Ui.ACCENT,()->options(f)));
        }
    }
    private void options(File f) {
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(new String[]{L.t("Run","Запустить"),L.t("Delete","Удалить")},(d,i)->{
            if(i==0) run(f.getName(),f.length()>4L<<30?4096:8192);
            else { if(RuntimeService.llamaRunning&&f.getName().equals(RuntimeService.llamaModel)) startService(new Intent(this,RuntimeService.class).setAction("llama-stop")); f.delete(); drawMine(); }
        }).show();
    }
    private void run(String model,int ctx) {
        if(RuntimeService.llamaRunning) { note.setText(L.t("Stop the running model first.","Сначала остановите запущенную модель.")); return; }
        startForegroundService(new Intent(this,RuntimeService.class).setAction("llama-start").putExtra("model",model).putExtra("ctx",ctx));
        note.setText(L.t("Loading the model; the first load of a large file can take a minute.","Загружаем модель; первая загрузка большого файла может занять минуту."));
    }
    private void useInOpenCode() {
        RuntimeService.openWhenReady=true; // the core re-reads its config on start, so restart it to pick up the local provider
        startForegroundService(new Intent(this,RuntimeService.class).setAction("core-restart"));
        finish();
    }
    private void drawPicks() {
        picks.removeAllViews();
        for(String[] m:PICKS) {
            boolean have=new File(dir,m[1]).exists();
            picks.addView(ui.item(m[2],L.t(m[3],m[4])+" · "+mb(Long.parseLong(m[5])<<20),have?L.t("downloaded ✓","скачано ✓"):L.t("download","скачать"),have?Ui.GREEN:Ui.ACCENT,()->{ if(have) run(m[1],Integer.parseInt(m[6])); else download(m[0],m[1]); }));
        }
    }
    private void search(String query) {
        results.removeAllViews();results.addView(ui.text(L.t("Searching…","Ищем…"),13,Ui.MUTED));
        worker.execute(() -> { try {
            JSONArray a=new JSONArray(Updater.get("https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=25&search="+URLEncoder.encode(query,"UTF-8")));
            main.post(() -> { results.removeAllViews(); for(int i=0;i<a.length();i++) { JSONObject m=a.optJSONObject(i); if(m==null) continue; String id=m.optString("id"); results.addView(ui.item(id,"↓ "+fmt(m.optLong("downloads"))+"   ♥ "+m.optLong("likes"),"files ›",Ui.BLUE,()->files(id))); } if(a.length()==0) results.addView(ui.text(L.t("Nothing found","Ничего не найдено"),13,Ui.DIM)); });
        } catch(Exception e) { main.post(() -> { results.removeAllViews(); results.addView(ui.text(L.t("Search failed: ","Поиск не удался: ")+e.getMessage(),13,Ui.RED)); }); } });
    }
    private String fmt(long n) { return n>=1_000_000?String.format(Locale.US,"%.1fM",n/1e6):n>=1000?(n/1000)+"k":""+n; }
    private void files(String repo) {
        note.setText(L.t("Loading files…","Загружаем список файлов…"));
        worker.execute(() -> { try {
            JSONArray a=new JSONArray(Updater.get("https://huggingface.co/api/models/"+repo+"/tree/main"));
            List<String> names=new ArrayList<>(),labels=new ArrayList<>();
            for(int i=0;i<a.length();i++) { JSONObject f=a.getJSONObject(i); String n=f.optString("path"); if(!n.endsWith(".gguf")||n.contains("mmproj")||n.matches(".*-\\d{5}-of-\\d{5}\\.gguf")) continue; names.add(n); labels.add(n+"  ("+mb(f.optLong("size"))+")"); }
            main.post(() -> { note.setText(""); if(names.isEmpty()) { note.setText(L.t("No single-file GGUF in this repository.","В репозитории нет одиночных GGUF-файлов.")); return; }
                new AlertDialog.Builder(this).setTitle(repo).setItems(labels.toArray(new String[0]),(d,i)->download(repo,names.get(i))).show(); });
        } catch(Exception e) { main.post(() -> note.setText(L.t("Could not load: ","Не удалось загрузить: ")+e.getMessage())); } });
    }
    private void download(String repo,String file) {
        if(dlActive) { note.setText(L.t("A download is already running.","Уже идёт загрузка.")); return; }
        dlActive=true; dlError=""; dlName=file; dlDone=0; dlTotal=0;
        File target=new File(dir,new File(file).getName()), part=new File(dir,target.getName()+".part");
        Thread t=new Thread(() -> { try {
            long have=part.exists()?part.length():0;
            HttpURLConnection c=(HttpURLConnection)new URL("https://huggingface.co/"+repo+"/resolve/main/"+file).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent","opencode-android"); if(have>0) c.setRequestProperty("Range","bytes="+have+"-");
            int code=c.getResponseCode(); if(code!=200&&code!=206) throw new IOException("HTTP "+code);
            if(code==200) have=0;
            dlTotal=have+c.getContentLengthLong(); dlDone=have;
            if(dir.getUsableSpace()<dlTotal-have+(200L<<20)) throw new IOException(L.t("Not enough free storage","Недостаточно свободной памяти"));
            try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(part,code==206)) { byte[] b=new byte[1<<16]; int n; while((n=in.read(b))>0) { out.write(b,0,n); dlDone+=n; } }
            if(!part.renameTo(target)) throw new IOException("rename failed");
        } catch(Exception e) { dlError=L.t("Download failed (it will resume): ","Загрузка прервана (продолжится с места): ")+e.getMessage(); }
        finally { dlActive=false; } },"model-download");
        t.start(); drawRun();
    }
}
