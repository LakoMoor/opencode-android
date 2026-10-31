package ai.opencode.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.concurrent.*;

/** Home screen: launches the original OpenCode UI and hosts the app's own tools (models, hub, terminal, island, updates). */
public class MainActivity extends Activity {
    private final int muted=Ui.MUTED, TEXT=Ui.TEXT;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Ui ui;
    private TextView status, dot, modelLine, cursor;
    private Button launch;
    private LinearLayout updateSlot, islandRow;
    private TextView islandState;
    private ProgressBar progress;
    private boolean active,opening;
    private Updater.Release release;
    @Override public void onCreate(Bundle state) { super.onCreate(state); ui=new Ui(this); build(); if(state==null&&!RuntimeService.running&&!RuntimeService.ready) start(); checkUpdate(false); }
    private int dp(int n) { return ui.dp(n); }
    private TextView text(String value,int size,int color) { return ui.text(value,size,color); }
    private LinearLayout column() { return ui.column(); }
    private Button button(String value,boolean primary,Runnable action) { return ui.button(value,primary,action); }

    private LinearLayout tile(String glyph,String title,String sub,int color,Runnable tap) {
        LinearLayout t=ui.column();t.setBackground(ui.ripple(Ui.PANEL,Ui.LINE,14));t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setOnClickListener(v->tap.run());
        t.addView(ui.mono(glyph,22,color));TextView a=ui.mono(title,15,Ui.TEXT);a.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);a.setPadding(0,dp(10),0,0);t.addView(a);
        TextView b=text(sub,12,Ui.MUTED);b.setPadding(0,dp(2),0,0);t.addView(b);return t;
    }
    private LinearLayout pair(View left,View right) {
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setLayoutParams(ui.lp(-1,-2,10));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-1,1),b=new LinearLayout.LayoutParams(0,-1,1);a.rightMargin=dp(5);b.leftMargin=dp(5);r.addView(left,a);r.addView(right,b);return r;
    }
    private void build() {
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setFillViewport(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content=column();content.setPadding(dp(20),dp(36),dp(20),dp(28));
        LinearLayout logo=ui.row();
        TextView open=ui.mono("open",34,Ui.MUTED);open.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);logo.addView(open);
        TextView code=ui.mono("code",34,Ui.TEXT);code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);logo.addView(code);
        cursor=ui.mono("▌",34,Ui.ACCENT);logo.addView(cursor);
        android.animation.ObjectAnimator blink=android.animation.ObjectAnimator.ofFloat(cursor,"alpha",1f,1f,0f,0f);blink.setDuration(1100);blink.setRepeatCount(android.animation.ValueAnimator.INFINITE);blink.start();
        content.addView(logo);
        TextView tag=ui.mono(L.t("the open source coding agent, in your pocket","открытый кодинг-агент — у вас в кармане"),12,Ui.MUTED);tag.setPadding(0,dp(6),0,0);content.addView(tag);
        updateSlot=column();content.addView(updateSlot);

        LinearLayout hero=column();hero.setBackground(ui.shape(Ui.PANEL,Ui.LINE,16));hero.setPadding(dp(16),dp(14),dp(16),dp(16));hero.setLayoutParams(ui.lp(-1,-2,22));
        LinearLayout head=ui.row();dot=ui.mono("●",14,Ui.DIM);head.addView(dot);
        TextView title=ui.mono("  opencode serve",13,Ui.MUTED);head.addView(title);hero.addView(head);
        status=text(RuntimeService.state,16,Ui.TEXT);status.setPadding(0,dp(10),0,dp(4));hero.addView(status);
        modelLine=ui.mono("",12,Ui.MUTED);hero.addView(modelLine);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));progress.setVisibility(View.GONE);hero.addView(progress,ui.lp(-1,dp(3),10));
        launch=button(L.t("Start OpenCode","Запустить OpenCode"),true,this::start);launch.setLayoutParams(ui.lp(-1,-2,14));hero.addView(launch);
        content.addView(hero);

        content.addView(ui.section(L.t("workspace","рабочее место")));
        content.addView(pair(tile("◆",L.t("Models","Модели"),L.t("Run LLMs on the phone · Hugging Face","LLM на телефоне · Hugging Face"),Ui.ACCENT,()->startActivity(new Intent(this,ModelsActivity.class))),
            tile("✦",L.t("Hub","Хаб"),L.t("Skills · plugins · MCP · agents","Навыки · плагины · MCP · агенты"),Ui.PURPLE,()->startActivity(new Intent(this,HubActivity.class)))));
        content.addView(pair(tile(">_",L.t("TUI","TUI"),L.t("Original terminal interface","Оригинальный терминальный интерфейс"),Ui.GREEN,()->terminal("opencode")),
            tile("$",L.t("Shell","Оболочка"),L.t("Ubuntu bash · git · python","Ubuntu bash · git · python"),Ui.BLUE,()->terminal("bash"))));

        content.addView(ui.section(L.t("system","система")));
        islandRow=ui.column();content.addView(islandRow);
        content.addView(button(L.t("Custom OpenAI-compatible server","Свой OpenAI-compatible сервер"),false,this::provider));
        content.addView(button(L.t("Check for updates","Проверить обновления"),false,()->checkUpdate(true)));
        content.addView(button(L.t("Local environment log","Журнал локальной среды"),false,this::logs));
        content.addView(button(L.t("Stop the core","Остановить ядро"),false,()->{stopService(new Intent(this,RuntimeService.class));opening=false;}));
        TextView note=ui.mono("v"+Updater.current(this)+"  ·  github.com/"+Updater.REPO,11,Ui.DIM);note.setPadding(dp(2),dp(24),0,0);content.addView(note);
        scroll.addView(content);setContentView(scroll);
    }
    private void drawIsland() {
        islandRow.removeAllViews();
        boolean on=Island.enabled(this),perm=Island.allowed(this),tap=Island.accessible();
        String sub=!perm?L.t("needs a permission — tap to set up","нужно разрешение — нажмите, чтобы настроить"):!on?L.t("off — notifications only","выключен — только уведомления"):tap?L.t("around the camera: progress, questions, results; tap the camera for quick actions","вокруг камеры: прогресс, вопросы, результат; нажмите на камеру — быстрые действия"):L.t("around the camera (display only). Enable accessibility mode to make the camera tappable","вокруг камеры (только показ). Включите режим специальных возможностей, чтобы камера нажималась");
        islandRow.addView(ui.item("Dynamic island",sub,on&&perm?"on":"off",on&&perm?Ui.GREEN:Ui.DIM,()->{
            if(!perm||(on&&!tap)) { islandSetup(perm); return; }
            Island.setEnabled(this,!on);drawIsland();if(!on)Island.notifyEvent(this,Island.INFO,L.t("Island is on","Остров включён"),L.t("tap the camera","нажмите на камеру"));
        }));
    }
    private void islandSetup(boolean perm) {
        new AlertDialog.Builder(this).setTitle("Dynamic island").setMessage(L.t("Accessibility mode (recommended) puts the island above the status bar so the camera is tappable. It only draws the island and reads nothing. Overlay mode only displays it.","Режим специальных возможностей (рекомендуется) ставит остров над строкой состояния, и на камеру можно нажимать. Он только рисует остров и ничего не читает. Режим оверлея только показывает остров."))
            .setPositiveButton(L.t("Accessibility","Спец. возможности"),(d,w)->Island.openSettings(this))
            .setNeutralButton(L.t("Overlay only","Только оверлей"),(d,w)->{if(Island.allowed(this)){Island.setEnabled(this,true);drawIsland();}else Island.requestPermission(this);})
            .setNegativeButton(L.t("Turn off","Выключить"),(d,w)->{Island.setEnabled(this,false);drawIsland();}).show();
    }
    private void checkUpdate(boolean manual) {
        long last=getSharedPreferences("runtime",0).getLong("updateCheck",0);
        if(!manual&&System.currentTimeMillis()-last<6*3600*1000L) return;
        worker.execute(() -> { try {
            Updater.Release r=Updater.latest(); getSharedPreferences("runtime",0).edit().putLong("updateCheck",System.currentTimeMillis()).apply();
            boolean fresh=r!=null&&Updater.newer(r.tag,Updater.current(this));
            runOnUiThread(() -> { if(isDestroyed()) return; release=fresh?r:null; drawUpdate(); if(manual&&!fresh) Toast.makeText(this,L.t("You have the latest version","У вас последняя версия"),Toast.LENGTH_SHORT).show(); });
        } catch(Exception e) { if(manual) runOnUiThread(() -> Toast.makeText(this,L.t("Could not check: ","Не удалось проверить: ")+e.getMessage(),Toast.LENGTH_LONG).show()); } });
    }
    private void drawUpdate() {
        updateSlot.removeAllViews(); if(release==null) return;
        LinearLayout c=ui.item(L.t("Update available: ","Доступно обновление: ")+release.tag,L.t("Tap to download and install ("+(release.size>>20)+" MB)","Нажмите, чтобы скачать и установить ("+(release.size>>20)+" МБ)"),"↓",Ui.ACCENT,this::downloadUpdate);
        c.setBackground(ui.ripple(0xff1d1510,Ui.ACCENT,12));((LinearLayout.LayoutParams)c.getLayoutParams()).topMargin=dp(18);updateSlot.addView(c);
    }
    private void downloadUpdate() {
        Updater.Release r=release; if(r==null) return;
        Toast.makeText(this,L.t("Downloading the update…","Скачиваем обновление…"),Toast.LENGTH_SHORT).show();
        worker.execute(() -> { try {
            File apk=Updater.download(this,r,(d,t) -> runOnUiThread(() -> { if(t>0&&!isDestroyed()) status.setText(L.t("Downloading update ","Скачиваем обновление ")+(d*100/t)+"%"); }));
            runOnUiThread(() -> { if(!isDestroyed()&&!Updater.install(this,apk)) Toast.makeText(this,L.t("Allow installing from this app, then tap the update again","Разрешите установку из этого приложения и нажмите на обновление снова"),Toast.LENGTH_LONG).show(); });
        } catch(Exception e) { runOnUiThread(() -> Toast.makeText(this,L.t("Update failed: ","Не удалось обновить: ")+e.getMessage(),Toast.LENGTH_LONG).show()); } });
    }
    private void terminal(String command) { startActivity(new Intent(this,TerminalActivity.class).putExtra(TerminalActivity.EXTRA_COMMAND,command)); }
    private void start() { opening=true; if(RuntimeService.ready) { open();return; } startForegroundService(new Intent(this,RuntimeService.class));status.setText(L.t("Starting…","Запускаем…")); }
    private void open() { if(!RuntimeService.ready) return;opening=false;startActivity(new Intent(this,WebActivity.class)); }
    private final Runnable refresh=new Runnable(){ public void run(){if(!active)return;
        if(RuntimeService.openWhenReady){RuntimeService.openWhenReady=false;opening=true;if(!RuntimeService.running)startForegroundService(new Intent(MainActivity.this,RuntimeService.class));}
        status.setText(RuntimeService.state);launch.setText(RuntimeService.ready?L.t("Open OpenCode","Открыть OpenCode"):RuntimeService.running?L.t("Preparing the environment…","Подготовка среды…"):L.t("Start OpenCode","Запустить OpenCode"));launch.setEnabled(!RuntimeService.running||RuntimeService.ready);launch.setAlpha(launch.isEnabled()?1f:0.5f);progress.setVisibility(RuntimeService.running&&!RuntimeService.ready?View.VISIBLE:View.GONE);
        dot.setTextColor(RuntimeService.ready?Ui.GREEN:RuntimeService.running?Ui.ACCENT:Ui.DIM);
        modelLine.setText(RuntimeService.llamaReady?"◆ "+RuntimeService.llamaModel:RuntimeService.llamaRunning?"◇ "+RuntimeService.llamaState:"");modelLine.setVisibility(modelLine.length()==0?View.GONE:View.VISIBLE);
        if(opening&&RuntimeService.ready)open();handler.postDelayed(this,700);} };
    @Override protected void onResume(){super.onResume();active=true;handler.post(refresh);drawIsland();}
    @Override protected void onPause(){active=false;handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void error(String message){new AlertDialog.Builder(this).setTitle("OpenCode").setMessage(message).setPositiveButton(L.t("OK","Понятно"),null).show();}
    private void logs(){TextView t=text(RuntimeService.logs(),12,Color.WHITE);t.setTypeface(Typeface.MONOSPACE);t.setTextIsSelectable(true);t.setPadding(dp(12),dp(12),dp(12),dp(12));ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle(L.t("Local core","Локальное ядро")).setView(s).setPositiveButton(L.t("Close","Закрыть"),null).show();}
    private EditText field(LinearLayout form,String label,String value,boolean secret){TextView t=text(label,13,muted);t.setPadding(0,dp(16),0,0);form.addView(t);EditText e=new EditText(this);e.setText(value);e.setSingleLine();e.setTextColor(TEXT);e.setHintTextColor(muted);e.setTextSize(15);e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));form.addView(e);return e;}
    private void provider(){
        JSONObject saved=new JSONObject();try{saved=new JSONObject(new Vault(this).load());}catch(Exception ignored){}
        LinearLayout form=column();form.setPadding(dp(20),0,dp(20),dp(12));
        form.addView(text(L.t("These settings apply to the original local OpenCode core. All other providers and options are available in its UI.","Настройка применяется к оригинальному локальному ядру OpenCode. Все остальные провайдеры и настройки доступны в его интерфейсе."),13,muted));
        EditText base=field(form,L.t("Base URL (e.g. http://192.168.1.10:1234/v1)","Базовый URL (например http://192.168.1.10:1234/v1)"),saved.optString("base"),false);
        EditText key=field(form,L.t("API key (optional)","API key (необязательно)"),saved.optString("key"),true);
        EditText model=field(form,L.t("Model ID","ID модели"),saved.optString("model"),false);
        TextView feedback=text(L.t("HTTP sends the key in clear text. Use HTTPS for an external server.","HTTP передаёт ключ открыто. Для внешнего сервера используйте HTTPS."),12,muted);
        form.addView(button(L.t("Check and choose model","Проверить и выбрать модель"),false,()->{
            Config c=new Config();c.base=base.getText().toString().trim();c.key=key.getText().toString().trim();try{c.normalizedBase();}catch(Exception e){error(e.getMessage());return;}
            feedback.setText(L.t("Connecting…","Подключаемся…"));worker.execute(()->{try{
                JSONArray data=new JSONObject(new Api().request(c,"/models","GET",null)).getJSONArray("data");String[] names=new String[data.length()];for(int i=0;i<data.length();i++)names[i]=data.getJSONObject(i).getString("id");
                runOnUiThread(()->{if(isDestroyed())return;feedback.setText(L.t("Connection works","Подключение работает"));new AlertDialog.Builder(this).setTitle(L.t("Model","Модель")).setItems(names,(d,n)->model.setText(names[n])).show();});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())feedback.setText(L.t("Error: ","Ошибка: ")+e.getMessage()+L.t(". You can enter the model ID manually.",". ID модели можно ввести вручную."));});}});
        }));form.addView(feedback);ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(L.t("Custom model server","Свой сервер модели")).setView(scroll).setNegativeButton(L.t("Cancel","Отмена"),null).setPositiveButton(L.t("Save","Сохранить"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Config c=new Config();c.base=base.getText().toString().trim();c.key=key.getText().toString().trim();c.model=model.getText().toString().trim();
            try{c.base=c.normalizedBase();if(c.model.isEmpty())throw new Exception(L.t("Enter a model","Укажите модель"));if(RuntimeService.running&&!RuntimeService.ready)throw new Exception(L.t("Wait for the local environment to finish preparing.","Дождитесь завершения подготовки локальной среды."));
                new Vault(this).save(new JSONObject().put("base",c.base).put("key",c.key).put("model",c.model).toString());
                // Store a pending configuration: the rootfs may not exist before the first launch.
                getSharedPreferences("runtime",0).edit().putBoolean("applyProvider",true).apply();dialog.dismiss();
                if(RuntimeService.ready){stopService(new Intent(this,RuntimeService.class));handler.postDelayed(()->{if(!isDestroyed())start();},1200);}
            }catch(Exception e){error(e.getMessage());}
        }));dialog.show();
    }
}
