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
import java.util.concurrent.*;

/** Android bootstrap only; all coding screens are provided by the original OpenCode app. */
public class MainActivity extends Activity {
    private final int muted=0xff93a090;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;
    private Button launch;
    private boolean active,opening;
    @Override public void onCreate(Bundle state) { super.onCreate(state); build(); if(state==null&&!RuntimeService.running&&!RuntimeService.ready) start(); }
    private static final int BG=0xff101211,CARD=0xff1b1e1c,ACCENT=0xffb9f589,TEXT=0xfff2f4f1;
    private android.widget.ProgressBar progress;
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n); }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(0,1.15f);return t; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private GradientDrawable shape(int fill,int stroke,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g; }
    private Button button(String value,boolean primary,Runnable action) {
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(primary?17:15);b.setTypeface(Typeface.DEFAULT,primary?Typeface.BOLD:Typeface.NORMAL);
        b.setTextColor(primary?0xff0d1a05:TEXT);b.setStateListAnimator(null);b.setMinHeight(dp(primary?56:48));b.setMinimumHeight(dp(primary?56:48));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),primary?shape(ACCENT,0,16):shape(CARD,0xff2e332f,16),null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);b.setLayoutParams(lp);b.setOnClickListener(v->action.run());return b;
    }
    private LinearLayout card() { LinearLayout c=column();c.setBackground(shape(CARD,0xff2e332f,20));c.setPadding(dp(18),dp(16),dp(18),dp(18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);c.setLayoutParams(lp);return c; }
    private void build() {
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content=column();content.setPadding(dp(22),dp(28),dp(22),dp(24));
        TextView logo=text("opencode",30,ACCENT);logo.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);content.addView(logo);
        TextView tag=text(L.t("A full AI coding agent right on your phone — no computer, and no internet needed to install.","Полноценный AI-агент для кода — прямо на телефоне, без компьютера и интернета для установки."),15,muted);tag.setPadding(0,dp(8),0,0);content.addView(tag);
        LinearLayout hero=card();
        TextView stateTitle=text(L.t("Status","Состояние"),12,muted);hero.addView(stateTitle);
        status=text(RuntimeService.state,17,TEXT);status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);status.setPadding(0,dp(6),0,dp(12));hero.addView(status);
        progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));progress.setVisibility(View.GONE);hero.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        launch=button(L.t("Start OpenCode","Запустить OpenCode"),true,this::start);hero.addView(launch);
        content.addView(hero);
        TextView more=text(L.t("Tools","Инструменты"),12,muted);more.setPadding(dp(4),dp(24),0,dp(2));content.addView(more);
        content.addView(button(L.t("OpenCode terminal (TUI)","Терминал OpenCode (TUI)"),false,()->terminal("opencode")));
        content.addView(button(L.t("Linux shell (bash)","Оболочка Linux (bash)"),false,()->terminal("bash")));
        content.addView(button(L.t("Custom OpenAI-compatible server","Свой OpenAI-compatible сервер"),false,this::provider));
        content.addView(button(L.t("Local environment log","Журнал локальной среды"),false,this::logs));
        content.addView(button(L.t("Stop the core","Остановить ядро"),false,()->{stopService(new Intent(this,RuntimeService.class));opening=false;}));
        TextView note=text(L.t("Everything is inside the APK: Ubuntu, Git, Python, curl, ripgrep, SSH. The first launch unpacks the environment (about a minute, no internet needed; ~1.5 GB of storage). Back on the work screen returns here; projects live in /workspace.","Всё уже внутри APK: Ubuntu, Git, Python, curl, ripgrep, SSH. Первый запуск распаковывает среду (около минуты, интернет не нужен; нужно ~1,5 ГБ памяти). Кнопка «Назад» в рабочем экране возвращает сюда, проекты лежат в /workspace."),12,muted);note.setPadding(dp(4),dp(22),dp(4),0);content.addView(note);
        scroll.addView(content);setContentView(scroll);
    }
    private void terminal(String command) { startActivity(new Intent(this,TerminalActivity.class).putExtra(TerminalActivity.EXTRA_COMMAND,command)); }
    private void start() { opening=true; if(RuntimeService.ready) { open();return; } startForegroundService(new Intent(this,RuntimeService.class));status.setText(L.t("Starting…","Запускаем…")); }
    private void open() { if(!RuntimeService.ready) return;opening=false;startActivity(new Intent(this,WebActivity.class)); }
    private final Runnable refresh=new Runnable(){ public void run(){if(!active)return;status.setText(RuntimeService.state);launch.setText(RuntimeService.ready?L.t("Open OpenCode","Открыть OpenCode"):RuntimeService.running?L.t("Preparing the environment…","Подготовка среды…"):L.t("Start OpenCode","Запустить OpenCode"));launch.setEnabled(!RuntimeService.running||RuntimeService.ready);launch.setAlpha(launch.isEnabled()?1f:0.5f);progress.setVisibility(RuntimeService.running&&!RuntimeService.ready?View.VISIBLE:View.GONE);if(opening&&RuntimeService.ready)open();handler.postDelayed(this,700);} };
    @Override protected void onResume(){super.onResume();active=true;handler.post(refresh);}
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
