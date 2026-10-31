package ai.opencode.android;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hosts the island window in the accessibility overlay layer, which sits above the status bar,
 * so taps on the camera reach the island. It never reads the screen: events are ignored.
 */
public class IslandService extends AccessibilityService {
    static volatile IslandService instance;
    @Override protected void onServiceConnected() { super.onServiceConnected(); instance=this; Island.rebuild(this); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public boolean onUnbind(android.content.Intent intent) { instance=null; Island.rebuild(getApplicationContext()); return super.onUnbind(intent); }
}
