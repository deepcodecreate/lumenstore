package com.opensourcefinder.app.ui;

import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class KeyboardFocusHelper {

    private KeyboardFocusHelper() {}

    public static void attach(@NonNull EditText input) {
        final boolean[] imeWasVisible = {false};
        final ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(input);
            if (insets == null) return;
            boolean visible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (imeWasVisible[0] && !visible && input.hasFocus()) release(input);
            imeWasVisible[0] = visible;
        };
        input.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View v) {
                imeWasVisible[0] = false;
                ViewTreeObserver observer = v.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnGlobalLayoutListener(listener);
            }

            @Override public void onViewDetachedFromWindow(@NonNull View v) {
                ViewTreeObserver observer = v.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
                imeWasVisible[0] = false;
            }
        });
        if (input.isAttachedToWindow()) {
            ViewTreeObserver observer = input.getViewTreeObserver();
            if (observer.isAlive()) observer.addOnGlobalLayoutListener(listener);
        }
    }

    private static void release(EditText input) {
        input.setFocusableInTouchMode(false);
        input.clearFocus();
        input.setFocusableInTouchMode(true);
    }
}
