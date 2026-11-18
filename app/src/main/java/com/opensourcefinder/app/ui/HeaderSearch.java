/*
 * This file is part of OpenSourceFinder.
 *
 * Copyright (C) 2026 deepcodecreate
 *
 * OpenSourceFinder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenSourceFinder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenSourceFinder. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.opensourcefinder.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.opensourcefinder.app.R;

public final class HeaderSearch {

    public interface Listener {

        void onQuery(@NonNull String query, boolean immediate);
    }

    private final View row;
    private final View logo;
    private final ImageView logoIcon;
    private final TextView title;
    private final MaterialCardView card;
    private final EditText input;
    private final ImageView clear;
    private final ImageView searchIcon;
    private final View spacer;
    private final android.widget.LinearLayout actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long debounceMs;
    private final Listener listener;
    private Runnable pending;
    private ValueAnimator strokeAnimator;
    private boolean silent;

    private HeaderSearch(View root, long debounceMs, Listener listener) {
        this.row = root.findViewById(R.id.headerRow);
        this.logo = root.findViewById(R.id.headerLogo);
        this.logoIcon = root.findViewById(R.id.headerLogoIcon);
        this.title = root.findViewById(R.id.headerTitle);
        this.card = root.findViewById(R.id.headerSearchCard);
        this.input = root.findViewById(R.id.headerSearchInput);
        this.clear = root.findViewById(R.id.headerSearchClear);
        this.searchIcon = root.findViewById(R.id.headerSearchIcon);
        this.spacer = root.findViewById(R.id.headerSpacer);
        this.actions = root.findViewById(R.id.headerActions);
        this.debounceMs = debounceMs;
        this.listener = listener;
    }

    public static HeaderSearch bind(@NonNull View root, @DrawableRes int logoRes, @NonNull CharSequence titleText,
                                    @StringRes int hintRes, long debounceMs, @NonNull Listener listener) {
        HeaderSearch h = new HeaderSearch(root, debounceMs, listener);
        if (h.logoIcon != null && logoRes != 0) h.logoIcon.setImageResource(logoRes);
        if (h.title != null) h.title.setText(titleText);
        if (h.input != null) h.input.setHint(hintRes);
        h.setup();
        return h;
    }

    private void setup() {
        if (input == null) return;
        KeyboardFocusHelper.attach(input);
        Context context = input.getContext();
        if (clear != null) {
            clear.setImageResource(TablerIconRes.get(context, "ic_x"));
            clear.setOnClickListener(v -> {
                input.setText("");
                input.requestFocus();
            });
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s == null ? "" : s.toString().trim();
                setClearVisible(s != null && s.length() > 0);
                if (silent) return;
                if (pending != null) handler.removeCallbacks(pending);
                final String q = value;
                pending = () -> listener.onQuery(q, false);
                handler.postDelayed(pending, q.isEmpty() ? 0L : debounceMs);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        input.setOnFocusChangeListener((v, hasFocus) -> animateFocus(hasFocus));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (pending != null) handler.removeCallbacks(pending);
                listener.onQuery(query(), true);
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    @NonNull public String query() {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    public boolean hasFocusOrText() {
        return input != null && (input.hasFocus() || (input.getText() != null && input.getText().length() > 0));
    }

    public void reset() {
        if (input == null) return;
        silent = true;
        input.setText("");
        silent = false;
        input.clearFocus();
        hideKeyboard();
    }

    public void clearAndNotify() {
        if (input == null) return;
        input.setText("");
        input.clearFocus();
        hideKeyboard();
    }

    public void setHint(@StringRes int hintRes) { if (input != null) input.setHint(hintRes); }

    public void setTitle(CharSequence text) { if (title != null) title.setText(text); }

    public void setRowVisible(boolean visible) {
        if (row != null) row.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public View logo() { return logo; }

    public android.widget.LinearLayout actions() { return actions; }

    public void setSearchVisible(boolean visible) {
        if (card != null) {
            card.setVisibility(visible ? View.VISIBLE : View.GONE);
            android.view.ViewGroup.LayoutParams lp = card.getLayoutParams();
            if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams llp = (android.widget.LinearLayout.LayoutParams) lp;
                llp.weight = visible ? 1f : 0f;
                llp.width = visible ? 0 : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                card.setLayoutParams(llp);
            }
        }
        if (spacer != null) {
            spacer.setVisibility(visible ? View.GONE : View.VISIBLE);
            android.view.ViewGroup.LayoutParams lp = spacer.getLayoutParams();
            if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams llp = (android.widget.LinearLayout.LayoutParams) lp;
                llp.weight = visible ? 0f : 1f;
                spacer.setLayoutParams(llp);
            }
        }
        if (!visible && input != null) {
            silent = true;
            input.setText("");
            silent = false;
            hideKeyboard();
        }
    }

    public ImageView logoIcon() { return logoIcon; }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (strokeAnimator != null) strokeAnimator.cancel();
    }

    private void hideKeyboard() {
        if (input == null) return;
        Object service = input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager) {
            ((InputMethodManager) service).hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void setClearVisible(boolean visible) {
        if (clear == null) return;
        long in = MotionHelper.getDuration(clear.getContext(), 200, 320);
        long out = MotionHelper.getDuration(clear.getContext(), 120, 180);
        if (visible && clear.getVisibility() != View.VISIBLE) {
            clear.animate().cancel();
            clear.setVisibility(View.VISIBLE);
            clear.setAlpha(0f);
            clear.setScaleX(0.5f);
            clear.setScaleY(0.5f);
            clear.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(in)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
        } else if (!visible && clear.getVisibility() == View.VISIBLE) {
            clear.animate().cancel();
            clear.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(out)
                    .withEndAction(() -> clear.setVisibility(View.GONE)).start();
        }
    }

    private void animateFocus(boolean focused) {
        if (card == null) return;
        Context context = card.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int from = card.getStrokeWidth();
        int to = focused ? Math.round(2f * density) : 0;
        if (strokeAnimator != null) strokeAnimator.cancel();
        long duration = MotionHelper.getDuration(context, 200, 320);
        if (duration == 0L) {
            card.setStrokeWidth(to);
        } else {
            strokeAnimator = ValueAnimator.ofInt(from, to);
            strokeAnimator.setDuration(duration);
            strokeAnimator.addUpdateListener(a -> card.setStrokeWidth((int) a.getAnimatedValue()));
            strokeAnimator.start();
        }
        int tint = MaterialColors.getColor(card, focused
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        if (searchIcon != null) {
            ImageViewCompat.setImageTintList(searchIcon, ColorStateList.valueOf(tint));
            searchIcon.animate().cancel();
            searchIcon.setScaleX(0.8f);
            searchIcon.setScaleY(0.8f);
            searchIcon.animate().scaleX(1f).scaleY(1f).setDuration(duration)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
        }
    }
}
