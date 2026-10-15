package com.agentreach.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Shared visual tokens for the dependency-free native interface. */
public final class AgentTheme {
    public static final int INK = Color.rgb(7, 10, 20);
    public static final int SURFACE = Color.rgb(14, 20, 36);
    public static final int SURFACE_HIGH = Color.rgb(20, 29, 49);
    public static final int BORDER = Color.rgb(36, 50, 76);
    public static final int BLUE = Color.rgb(10, 132, 255);
    public static final int CYAN = Color.rgb(20, 200, 255);
    public static final int TEXT = Color.rgb(247, 250, 255);
    public static final int MUTED = Color.rgb(155, 171, 197);
    public static final int GREEN = Color.rgb(66, 211, 146);
    public static final int RED = Color.rgb(255, 108, 119);

    private AgentTheme() {}

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable background(int color, float radiusDp, int strokeColor, float strokeDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setLineSpacing(0, 1.12f);
        text.setFontFeatureSettings("kern");
        return text;
    }

    public static TextView label(Context context, String value) {
        TextView text = text(context, value.toUpperCase(Locale.ROOT), 11, MUTED);
        text.setLetterSpacing(0.14f);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    public static TextView pill(Context context, String value, int color) {
        TextView pill = text(context, value, 12, color);
        pill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7));
        pill.setBackground(background(SURFACE_HIGH, 99, color, 1, context));
        return pill;
    }

    public static TextView button(Context context, String value, boolean primary) {
        TextView button = text(context, value, 14, primary ? Color.WHITE : TEXT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13));
        button.setMinHeight(dp(context, 48));
        button.setBackground(background(primary ? BLUE : SURFACE_HIGH, 14,
                primary ? BLUE : BORDER, 1, context));
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(context, 2));
        return button;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        card.setBackground(background(SURFACE, 20, BORDER, 1, context));
        return card;
    }

    public static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }
}
