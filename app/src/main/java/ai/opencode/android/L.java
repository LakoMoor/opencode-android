package ai.opencode.android;

import java.util.Locale;

/** Tiny localisation helper: English by default, Russian when the device language is Russian. */
final class L {
    private L() {}
    static String t(String en,String ru) { return "ru".equals(Locale.getDefault().getLanguage())?ru:en; }
}
