package ai.opencode.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared look: the OpenCode terminal aesthetic (near-black, monospace, warm peach accent). */
final class Ui {
    static final int BG=0xff0a0a0a, PANEL=0xff141414, LINE=0xff262626, TEXT=0xffeeeeee, MUTED=0xff808080, DIM=0xff4a4a4a, ACCENT=0xfffab283, BLUE=0xff5c9cf5, GREEN=0xff7fd88f, RED=0xffe06c75, PURPLE=0xff9d7cd8;
    final Activity a;
    Ui(Activity a) { this.a=a; }
    int dp(int n) { return (int)(a.getResources().getDisplayMetrics().density*n); }
    TextView text(String value,int size,int color) { TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(0,1.18f);return t; }
    TextView mono(String value,int size,int color) { TextView t=text(value,size,color);t.setTypeface(Typeface.MONOSPACE);return t; }
    LinearLayout column() { LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l; }
    LinearLayout row() { LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    GradientDrawable shape(int fill,int stroke,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g; }
    Drawable ripple(int fill,int stroke,int radius) { return new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape(fill,stroke,radius),null); }
    LinearLayout.LayoutParams lp(int w,int h,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p; }
    Button button(String value,boolean primary,Runnable action) {
        Button b=new Button(a);b.setText(value);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.MONOSPACE,primary?Typeface.BOLD:Typeface.NORMAL);
        b.setTextColor(primary?0xff1a0f08:TEXT);b.setStateListAnimator(null);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setBackground(ripple(primary?ACCENT:PANEL,primary?0:LINE,10));b.setLayoutParams(lp(-1,-2,10));b.setOnClickListener(v->action.run());return b;
    }
    /** Tappable card: title, one-line description, optional trailing badge text. */
    LinearLayout item(String title,String desc,String badge,int badgeColor,Runnable tap) {
        LinearLayout c=column();c.setBackground(ripple(PANEL,LINE,12));c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setLayoutParams(lp(-1,-2,8));
        LinearLayout head=row();TextView t=mono(title,15,TEXT);t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);head.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        if(badge!=null){TextView b=mono(badge,11,badgeColor);head.addView(b);}
        c.addView(head);if(desc!=null&&!desc.isEmpty()){TextView d=text(desc,13,MUTED);d.setPadding(0,dp(4),0,0);c.addView(d);}
        if(tap!=null)c.setOnClickListener(v->tap.run());return c;
    }
    TextView section(String value) { TextView t=mono("// "+value.toLowerCase(),12,DIM);t.setPadding(dp(2),dp(22),0,dp(2));return t; }
    /** Screen scaffold: top bar with back arrow and title, scrolling content column. Returns the content column. */
    LinearLayout screen(String title) {
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout bar=row();bar.setPadding(dp(8),dp(10),dp(16),dp(6));
        TextView back=mono("←",22,TEXT);back.setPadding(dp(12),dp(6),dp(16),dp(6));back.setOnClickListener(v->a.finish());bar.addView(back);
        TextView t=mono(title,17,TEXT);t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);bar.addView(t);root.addView(bar);
        ScrollView scroll=new ScrollView(a);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content=column();content.setPadding(dp(18),dp(4),dp(18),dp(28));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        a.setContentView(root);return content;
    }
}
