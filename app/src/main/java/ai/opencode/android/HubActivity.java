package ai.opencode.android;

import android.app.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Hub for skills, slash commands, agents, MCP servers and plugins. */
public class HubActivity extends Activity {
    private Ui ui; private Hub hub; private LinearLayout list, tabs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private List<JSONObject> items=new ArrayList<>(); private String tab="skill"; private TextView note;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); ui=new Ui(this); hub=new Hub(this);
        LinearLayout c=ui.screen("hub");
        c.addView(ui.text(L.t("Extend the agent: skills, slash commands, subagents, MCP servers and plugins. Installs go straight into OpenCode's config.","Расширяйте агента: навыки, slash-команды, субагенты, MCP-серверы и плагины. Установка пишется прямо в конфиг OpenCode."),13,Ui.MUTED));
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);tabs=ui.row();hs.addView(tabs);c.addView(hs,ui.lp(-1,-2,14));
        note=ui.text("",13,Ui.ACCENT);note.setPadding(0,ui.dp(8),0,0);c.addView(note);
        list=ui.column();c.addView(list);
        c.addView(ui.section(L.t("more","ещё")));
        c.addView(ui.button(L.t("Install a skill from a GitHub link","Установить навык по ссылке GitHub"),false,this::fromUrl));
        c.addView(ui.button(L.t("Update catalog from GitHub","Обновить каталог из GitHub"),false,this::refresh));
        try { items=hub.items(); } catch(Exception e) { note.setText(e.getMessage()); }
        draw();
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    private String label(String t) { switch(t) { case "skill":return L.t("Skills","Навыки"); case "plugin":return L.t("Plugins","Плагины"); case "mcp":return "MCP"; case "command":return L.t("Commands","Команды"); default:return L.t("Agents","Агенты"); } }
    private void draw() {
        tabs.removeAllViews();
        for(String t:new String[]{"skill","plugin","mcp","command","agent"}) {
            TextView chip=ui.mono(label(t),13,t.equals(tab)?0xff1a0f08:Ui.TEXT);chip.setPadding(ui.dp(14),ui.dp(8),ui.dp(14),ui.dp(8));
            chip.setBackground(ui.shape(t.equals(tab)?Ui.ACCENT:Ui.PANEL,t.equals(tab)?0:Ui.LINE,20));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=ui.dp(8);chip.setOnClickListener(v->{tab=t;draw();});tabs.addView(chip,lp);
        }
        list.removeAllViews();
        for(JSONObject it:items) {
            if(!it.optString("type").equals(tab)) continue;
            boolean on=hub.installed(it);
            String desc=L.t(it.optString("desc"),it.optString("desc_ru",it.optString("desc")));
            list.addView(ui.item(it.optString("title"),desc,on?L.t("installed ✓","установлено ✓"):L.t("install","установить"),on?Ui.GREEN:Ui.ACCENT,()->toggle(it,on)));
        }
    }
    private void toggle(JSONObject it,boolean on) {
        worker.execute(() -> { try { if(on) hub.remove(it); else hub.install(it); String m=on?L.t("Removed. Restart the core to apply.","Удалено. Перезапустите ядро, чтобы применить."):L.t("Installed. Restart the core to apply.","Установлено. Перезапустите ядро, чтобы применить."); main.post(() -> { note.setText(m); draw(); }); }
            catch(Exception e) { main.post(() -> note.setText(e.getMessage())); } });
    }
    private void refresh() { note.setText(L.t("Updating…","Обновляем…")); worker.execute(() -> { try { int n=hub.refresh(); List<JSONObject> l=hub.items(); main.post(() -> { items=l; note.setText(L.t("Catalog updated: ","Каталог обновлён: ")+n); draw(); }); } catch(Exception e) { main.post(() -> note.setText(L.t("Could not update: ","Не удалось обновить: ")+e.getMessage())); } }); }
    private void fromUrl() {
        EditText e=new EditText(this);e.setHint("https://github.com/owner/repo/blob/main/skills/x/SKILL.md");e.setSingleLine();e.setInputType(InputType.TYPE_TEXT_VARIATION_URI);e.setTextColor(Ui.TEXT);e.setHintTextColor(Ui.DIM);
        new AlertDialog.Builder(this).setTitle(L.t("Skill link","Ссылка на навык")).setView(e).setNegativeButton(L.t("Cancel","Отмена"),null).setPositiveButton(L.t("Install","Установить"),(d,w)->{
            String url=e.getText().toString(); note.setText(L.t("Downloading…","Загружаем…"));
            worker.execute(() -> { try { String n=hub.installSkillFromUrl(url); main.post(() -> { note.setText(L.t("Skill installed: ","Навык установлен: ")+n); draw(); }); } catch(Exception x) { main.post(() -> note.setText(x.getMessage())); } });
        }).show();
    }
}
