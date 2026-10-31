package android.view;

import android.os.Bundle;

/** 只为 LifeCycle 的 AccessibilityDelegate 能实例化；不涉及真实 UI。 */
public class View {

    public static class AccessibilityDelegate {
        public AccessibilityDelegate() {
        }

        public boolean performAccessibilityAction(View view, int action, Bundle args) {
            return false;
        }

        public void onInitializeAccessibilityNodeInfo(View host, Object info) {
        }
    }

    public int getId() {
        return 0;
    }

    public Object getContext() {
        return null;
    }
}
