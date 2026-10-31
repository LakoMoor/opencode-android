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

/**
 * "Dynamic island": a black pill drawn over the front camera cut-out (centered at the top, as on the Galaxy S23 Ultra).
 * It sits compact while the agent works, expands for finished tasks, permission requests and errors,
 * and mirrors the important ones as heads-up notifications.
 */
final class Island {
    static final int INFO=0, BUSY=1, DONE=2, ASK=3, ERROR=4;
    private static final Handler ui=new Handler(Looper.getMainLooper());
    private static Context app;
    private static WindowManager wm;
    private static FrameLayout root;
    private static LinearLayout box;
    private static View dot;
    private static TextView title, sub, tick;
    private static WindowManager.LayoutParams params;
    private static boolean expanded, shown, busy;
    private static long busySince;
    private static int notificationId=100;

    static boolean allowed(Context c) { return Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(c); }
    static void requestPermission(Activity a) { a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.getPackageName()))); }
    static boolean enabled(Context c) { return c.getSharedPreferences("runtime",0).getBoolean("island",true); }
    static void setEnabled(Context c,boolean on) { c.getSharedPreferences("runtime",0).edit().putBoolean("island",on).apply(); if(!on) hide(); }

    private static int color(int kind) { switch(kind){ case BUSY:return Ui.ACCENT; case DONE:return Ui.GREEN; case ASK:return Ui.BLUE; case ERROR:return Ui.RED; default:return Ui.PURPLE; } }
    private static int dp(int n) { return (int)(app.getResources().getDisplayMetrics().density*n); }

    /** Shows or updates the island. Safe to call from any thread. */
    static void notifyEvent(Context context,int kind,String head,String detail) {
        app=context.getApplicationContext();
        if(kind==DONE||kind==ASK||kind==ERROR) postNotification(kind,head,detail);
        ui.post(() -> { if(enabled(app)&&allowed(app)) render(kind,head,detail); });
    }
    static void hide() { ui.post(() -> { if(shown&&root!=null) { try { wm.removeView(root); } catch(Exception ignored) {} } shown=false; expanded=false; busy=false; }); }

    private static void ensure() {
        if(shown) return;
        wm=app.getSystemService(WindowManager.class);
        root=new FrameLayout(app); box=new LinearLayout(app); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(dp(12),0,dp(14),0);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.BLACK); bg.setCornerRadius(dp(40)); box.setBackground(bg);
        dot=new View(app); GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(Ui.ACCENT); dot.setBackground(d);
        box.addView(dot,new LinearLayout.LayoutParams(dp(10),dp(10)));
        LinearLayout texts=new LinearLayout(app); texts.setOrientation(LinearLayout.VERTICAL); texts.setPadding(dp(10),0,0,0);
        title=new TextView(app); title.setTextColor(Color.WHITE); title.setTextSize(13); title.setTypeface(Typeface.DEFAULT_BOLD); title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub=new TextView(app); sub.setTextColor(0xffb0b0b0); sub.setTextSize(11); sub.setSingleLine(); sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title); texts.addView(sub); box.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        tick=new TextView(app); tick.setTextColor(0xffb0b0b0); tick.setTextSize(11); tick.setTypeface(Typeface.MONOSPACE); box.addView(tick);
        root.addView(box,new FrameLayout.LayoutParams(-1,-1));
        root.setOnClickListener(v -> { app.startActivity(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); collapse(); });
        params=new WindowManager.LayoutParams(dp(132),dp(34),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; params.y=dp(4); params.windowAnimations=android.R.style.Animation_Toast;
        if(Build.VERSION.SDK_INT>=28) params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try { wm.addView(root,params); shown=true; expanded=false; } catch(Exception e) { shown=false; }
    }

    private static final Runnable collapseLater=Island::collapse;
    private static final Runnable hideLater=() -> { if(!busy) hide(); };
    private static final Runnable clock=new Runnable() { public void run() { if(shown&&busy) { long s=(SystemClock.elapsedRealtime()-busySince)/1000; tick.setText(expanded?"":(s/60)+":"+String.format("%02d",s%60)); ui.postDelayed(this,1000); } } };

    private static void render(int kind,String head,String detail) {
        ensure(); if(!shown) return;
        ((GradientDrawable)dot.getBackground()).setColor(color(kind));
        title.setText(head); sub.setText(detail==null?"":detail); sub.setVisibility(detail==null||detail.isEmpty()?View.GONE:View.VISIBLE);
        ui.removeCallbacks(collapseLater); ui.removeCallbacks(hideLater);
        if(kind==BUSY) {
            if(!busy) { busy=true; busySince=SystemClock.elapsedRealtime(); ui.post(clock); }
            pulse(); layoutFor(false); animate(dp(210),dp(34)); expanded=false; sub.setVisibility(View.GONE);
        } else {
            busy=false; tick.setText("");
            layoutFor(true); animate(dp(300),dp(detail==null||detail.isEmpty()?70:92)); expanded=true;
            ui.postDelayed(collapseLater,kind==ASK?9000:5000);
        }
    }
    /** Expanded content sits below the camera cut-out; the compact pill keeps it centered in the camera row. */
    private static void layoutFor(boolean big) { dot.setTranslationY(big?dp(4):0); if(big) { box.setGravity(Gravity.TOP); box.setPadding(dp(18),dp(40),dp(18),dp(10)); } else { box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(dp(14),0,dp(16),0); } }
    private static void collapse() { expanded=false; layoutFor(false); if(busy) { animate(dp(210),dp(34)); sub.setVisibility(View.GONE); } else { animate(dp(132),dp(34)); ui.postDelayed(hideLater,350); } }
    private static void pulse() { ObjectAnimator a=ObjectAnimator.ofFloat(dot,"alpha",1f,0.25f,1f); a.setDuration(1200); a.setRepeatCount(ValueAnimator.INFINITE); a.start(); dot.setTag(a); }
    private static void animate(int w,int h) {
        if(params==null||root==null) return; int w0=params.width,h0=params.height;
        ValueAnimator v=ValueAnimator.ofFloat(0,1); v.setDuration(320); v.setInterpolator(new android.view.animation.OvershootInterpolator(0.9f));
        v.addUpdateListener(a -> { float f=(float)a.getAnimatedValue(); params.width=(int)(w0+(w-w0)*f); params.height=(int)(h0+(h-h0)*f); try { wm.updateViewLayout(root,params); } catch(Exception ignored) {} });
        v.start();
    }

    private static void postNotification(int kind,String head,String detail) {
        NotificationManager nm=app.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("events",L.t("Agent events","События агента"),NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open=PendingIntent.getActivity(app,2,new Intent(app,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        nm.notify(notificationId++%20+100,new Notification.Builder(app,"events").setSmallIcon(R.drawable.ic_launcher).setContentTitle(head).setContentText(detail).setContentIntent(open).setAutoCancel(true).build());
    }
}
