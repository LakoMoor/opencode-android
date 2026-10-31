package ai.opencode.android;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * "Dynamic island" around the front camera cut-out (centered at the top, as on the Galaxy S23 Ultra).
 * The row at camera height is split in two cells with a gap for the camera, so no text is ever hidden by it:
 *   idle     - invisible touch target right on the camera; tap opens the panel
 *   busy     - compact pill: pulsing dot on the left, timer on the right
 *   event    - wider pill: dot + title on the left, detail on the right (task finished, question, error)
 *   panel    - the pill grows downward: status, model, recent events and Open / Terminal / Stop actions
 */
final class Island {
    static final int INFO=0, BUSY=1, DONE=2, ASK=3, ERROR=4;
    private enum Mode { IDLE, BUSY, EVENT, PANEL }
    private static final Handler ui=new Handler(Looper.getMainLooper());
    private static Context app;
    private static WindowManager wm;
    private static FrameLayout root;
    private static LinearLayout box, panel, history;
    private static View dot;
    private static TextView title, detail, status, modelLine;
    private static GradientDrawable bg;
    private static WindowManager.LayoutParams params;
    private static boolean shown, busy;
    private static Mode mode=Mode.IDLE;
    private static long busySince;
    private static int kindNow=INFO, notificationId=100;
    private static String headNow="", detailNow="";
    private static final ArrayDeque<String> recent=new ArrayDeque<>();
    private static ValueAnimator morph;
    private static ObjectAnimator pulse;

    static boolean accessible() { return IslandService.instance!=null; }
    static boolean allowed(Context c) { return accessible()||Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(c); }
    /** Recreates the window when the accessibility host appears or goes away. */
    static void rebuild(Context c) { app=c.getApplicationContext(); ui.post(() -> { if(shown&&root!=null) { try { wm.removeView(root); } catch(Exception ignored) {} shown=false; } if(pulse!=null) pulse.cancel(); mode=Mode.IDLE; if(enabled(app)&&allowed(app)) { ensure(); settle(false); } }); }
    static void openSettings(Activity a) { a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
    static void requestPermission(Activity a) { a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.getPackageName()))); }
    static boolean enabled(Context c) { return c.getSharedPreferences("runtime",0).getBoolean("island",true); }
    static void setEnabled(Context c,boolean on) { c.getSharedPreferences("runtime",0).edit().putBoolean("island",on).apply(); if(on) start(c); else hide(); }

    private static int color(int kind) { switch(kind){ case BUSY:return Ui.ACCENT; case DONE:return Ui.GREEN; case ASK:return Ui.BLUE; case ERROR:return Ui.RED; default:return Ui.PURPLE; } }
    private static int dp(int n) { return (int)(app.getResources().getDisplayMetrics().density*n); }
    private static int screenW() { return app.getResources().getDisplayMetrics().widthPixels; }

    /** Puts the idle touch target on the camera (when enabled and permitted). */
    static void start(Context context) { app=context.getApplicationContext(); ui.post(() -> { if(enabled(app)&&allowed(app)) { ensure(); if(shown&&mode==Mode.IDLE) settle(false); } }); }
    /** Shows or updates the island. Safe to call from any thread. */
    static void notifyEvent(Context context,int kind,String head,String detail) {
        app=context.getApplicationContext();
        if(kind==DONE||kind==ASK||kind==ERROR) postNotification(kind,head,detail);
        ui.post(() -> { if(enabled(app)&&allowed(app)) render(kind,head,detail); });
    }
    static void hide() { ui.post(() -> { if(shown&&root!=null) { try { wm.removeView(root); } catch(Exception ignored) {} } shown=false; busy=false; mode=Mode.IDLE; if(pulse!=null) pulse.cancel(); }); }

    private static TextView label(int sp,int color,boolean bold) { TextView t=new TextView(app); t.setTextColor(color); t.setTextSize(sp); t.setSingleLine(); t.setEllipsize(android.text.TextUtils.TruncateAt.END); if(bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }

    private static void ensure() {
        if(shown) return;
        IslandService host=IslandService.instance;
        wm=(host!=null?host:app).getSystemService(WindowManager.class);
        root=new FrameLayout(app);
        box=new LinearLayout(app); box.setOrientation(LinearLayout.VERTICAL);
        bg=new GradientDrawable(); bg.setColor(Color.BLACK); bg.setCornerRadius(dp(22)); box.setBackground(bg);
        // camera row: [left cell][camera gap][right cell]
        LinearLayout bar=new LinearLayout(app); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(14),0,dp(14),0);
        LinearLayout left=new LinearLayout(app); left.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        dot=new View(app); GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(Ui.ACCENT); dot.setBackground(d); left.addView(dot,new LinearLayout.LayoutParams(dp(9),dp(9)));
        title=label(13,Color.WHITE,true); LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1); tl.leftMargin=dp(8); left.addView(title,tl);
        detail=label(12,0xffb0b0b0,false); detail.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        bar.addView(left,new LinearLayout.LayoutParams(0,-1,1)); bar.addView(new View(app),new LinearLayout.LayoutParams(dp(40),-1)); bar.addView(detail,new LinearLayout.LayoutParams(0,-1,1));
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(34)));
        // panel below the camera row
        panel=new LinearLayout(app); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20),dp(6),dp(20),dp(16)); panel.setVisibility(View.GONE);
        status=label(15,Color.WHITE,true); status.setSingleLine(false); panel.addView(status);
        modelLine=label(12,0xff9a9a9a,false); modelLine.setTypeface(Typeface.MONOSPACE); modelLine.setPadding(0,dp(3),0,0); panel.addView(modelLine);
        history=new LinearLayout(app); history.setOrientation(LinearLayout.VERTICAL); history.setPadding(0,dp(10),0,dp(2)); panel.addView(history);
        LinearLayout actions=new LinearLayout(app); actions.setPadding(0,dp(10),0,0);
        actions.addView(action(L.t("Open","Открыть"),true,Island::openApp),new LinearLayout.LayoutParams(0,dp(40),1));
        actions.addView(action("TUI",false,Island::openTerminal),new LinearLayout.LayoutParams(0,dp(40),1));
        actions.addView(action(L.t("Stop","Стоп"),false,Island::stopCore),new LinearLayout.LayoutParams(0,dp(40),1));
        for(int i=0;i<3;i++) { LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)actions.getChildAt(i).getLayoutParams(); p.leftMargin=i==0?0:dp(8); }
        panel.addView(actions); box.addView(panel);
        root.addView(box,new FrameLayout.LayoutParams(-1,-2));
        root.setOnClickListener(v -> { if(mode==Mode.PANEL) settle(true); else openPanel(); });
        root.setOnTouchListener((v,e) -> { if(e.getAction()==MotionEvent.ACTION_OUTSIDE&&mode==Mode.PANEL) { settle(true); } return false; });
        params=new WindowManager.LayoutParams(dp(64),dp(34),host!=null?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; params.y=dp(4);
        if(Build.VERSION.SDK_INT>=28) params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try { wm.addView(root,params); shown=true; mode=Mode.IDLE; bg.setAlpha(0); title.setVisibility(View.INVISIBLE); detail.setVisibility(View.INVISIBLE); dot.setVisibility(View.INVISIBLE); } catch(Exception e) { android.util.Log.w("OpenCodeIsland","addView failed",e); shown=false; }
    }
    private static TextView action(String text,boolean primary,Runnable run) {
        TextView t=label(13,primary?0xff1a0f08:Color.WHITE,true); t.setText(text); t.setGravity(Gravity.CENTER);
        GradientDrawable g=new GradientDrawable(); g.setColor(primary?Ui.ACCENT:0x22ffffff); g.setCornerRadius(dp(20)); t.setBackground(g);
        t.setOnClickListener(v -> { run.run(); settle(true); }); return t;
    }
    private static void openApp() { app.startActivity(new Intent(app,RuntimeService.ready?WebActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
    private static void openTerminal() { app.startActivity(new Intent(app,TerminalActivity.class).putExtra(TerminalActivity.EXTRA_COMMAND,"opencode").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    private static void stopCore() { app.stopService(new Intent(app,RuntimeService.class)); }

    private static final Runnable settleLater=() -> settle(true);
    private static final Runnable clock=new Runnable() { public void run() { if(shown&&busy) { long s=(SystemClock.elapsedRealtime()-busySince)/1000; if(mode==Mode.BUSY) detail.setText((s/60)+":"+String.format(Locale.US,"%02d",s%60)); ui.postDelayed(this,1000); } } };

    private static void remember(int kind,String head) {
        String t=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
        recent.addFirst((kind==ERROR?"✕ ":kind==ASK?"? ":"✓ ")+head+"  "+t); while(recent.size()>3) recent.removeLast();
    }
    private static void render(int kind,String head,String detailText) {
        ensure(); if(!shown) return;
        kindNow=kind; headNow=head; detailNow=detailText==null?"":detailText;
        ((GradientDrawable)dot.getBackground()).setColor(color(kind));
        ui.removeCallbacks(settleLater);
        if(kind==BUSY) { if(!busy) { busy=true; busySince=SystemClock.elapsedRealtime(); ui.post(clock); } }
        else { busy=false; if(kind!=INFO) remember(kind,head); }
        if(mode==Mode.PANEL) { fillPanel(); ui.postDelayed(settleLater,10000); return; }
        if(kind==BUSY) { if(mode!=Mode.EVENT) showBusy(); else { title.setText(head); detail.setText(detailNow); } }
        else showEvent(kind,head,detailNow);
    }
    private static void showBusy() {
        mode=Mode.BUSY; title.setText(""); title.setVisibility(View.GONE); dot.setVisibility(View.VISIBLE); detail.setVisibility(View.VISIBLE); detail.setTypeface(Typeface.MONOSPACE); detail.setText("0:00");
        panel.setVisibility(View.GONE); startPulse(); morph(dp(176),dp(34),255);
    }
    private static void showEvent(int kind,String head,String det) {
        mode=Mode.EVENT; stopPulse(); title.setVisibility(View.VISIBLE); title.setText(head); dot.setVisibility(View.VISIBLE); detail.setVisibility(View.VISIBLE); detail.setTypeface(Typeface.DEFAULT); detail.setText(det); panel.setVisibility(View.GONE);
        morph(Math.min(dp(350),screenW()-dp(24)),dp(34),255); ui.postDelayed(settleLater,kind==ASK?9000:5000);
    }
    private static void openPanel() {
        ensure(); if(!shown) return;
        ui.removeCallbacks(settleLater); mode=Mode.PANEL; title.setVisibility(View.VISIBLE); dot.setVisibility(View.VISIBLE); detail.setVisibility(View.VISIBLE);
        title.setText("opencode"); detail.setTypeface(Typeface.DEFAULT); detail.setText(RuntimeService.ready?L.t("running","работает"):RuntimeService.running?L.t("starting","запуск"):L.t("stopped","остановлен"));
        ((GradientDrawable)dot.getBackground()).setColor(RuntimeService.ready?Ui.GREEN:RuntimeService.running?Ui.ACCENT:0xff666666);
        fillPanel(); panel.setVisibility(View.VISIBLE);
        int w=Math.min(dp(350),screenW()-dp(24)); panel.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED);
        morph(w,dp(34)+panel.getMeasuredHeight(),255); ui.postDelayed(settleLater,10000);
    }
    private static void fillPanel() {
        status.setText(busy?headNow+(detailNow.isEmpty()?"":" · "+detailNow):headNow.isEmpty()?L.t("Waiting for your next message","Жду следующего сообщения"):headNow+(detailNow.isEmpty()?"":" · "+detailNow));
        modelLine.setText(RuntimeService.llamaReady?"◆ "+RuntimeService.llamaModel:"OpenCode 2.2.2 · "+L.t("local","локально")); 
        history.removeAllViews();
        for(String h:recent) { TextView t=label(12,0xff8a8a8a,false); t.setPadding(0,dp(2),0,0); t.setText(h); history.addView(t); }
    }
    /** Returns to the compact pill while the agent works, otherwise back to the invisible touch target on the camera. */
    private static void settle(boolean animate) {
        ui.removeCallbacks(settleLater); panel.setVisibility(View.GONE);
        if(busy) { ((GradientDrawable)dot.getBackground()).setColor(color(BUSY)); showBusy(); return; }
        mode=Mode.IDLE; stopPulse();
        morph(dp(64),dp(34),0); ui.postDelayed(() -> { if(mode==Mode.IDLE) { title.setVisibility(View.INVISIBLE); detail.setVisibility(View.INVISIBLE); dot.setVisibility(View.INVISIBLE); } },animate?260:0);
    }
    private static void startPulse() { if(pulse!=null) pulse.cancel(); pulse=ObjectAnimator.ofFloat(dot,"alpha",1f,0.25f,1f); pulse.setDuration(1200); pulse.setRepeatCount(ValueAnimator.INFINITE); pulse.start(); }
    private static void stopPulse() { if(pulse!=null) { pulse.cancel(); pulse=null; } dot.setAlpha(1f); }
    private static void morph(int w,int h,int alpha) {
        if(params==null||root==null) return; if(morph!=null) morph.cancel();
        int w0=params.width,h0=params.height,a0=bg.getAlpha();
        morph=ValueAnimator.ofFloat(0,1); morph.setDuration(300); morph.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        morph.addUpdateListener(a -> { float f=(float)a.getAnimatedValue(); params.width=(int)(w0+(w-w0)*f); params.height=(int)(h0+(h-h0)*f); bg.setAlpha((int)(a0+(alpha-a0)*f)); bg.setCornerRadius(dp(params.height>dp(40)?26:22)); try { wm.updateViewLayout(root,params); } catch(Exception ignored) {} });
        morph.start();
    }

    private static void postNotification(int kind,String head,String detail) {
        NotificationManager nm=app.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("events",L.t("Agent events","События агента"),NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open=PendingIntent.getActivity(app,2,new Intent(app,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        nm.notify(notificationId++%20+100,new Notification.Builder(app,"events").setSmallIcon(R.drawable.ic_launcher).setContentTitle(head).setContentText(detail).setContentIntent(open).setAutoCancel(true).build());
    }
}
