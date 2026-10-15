package com.agentreach.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Compact vector brand mark, drawn without an external UI dependency. */
public final class BrandMarkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BrandMarkView(Context context) {
        super(context);
        setContentDescription("Agent Reach");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.45f;
        float cy = h * 0.5f;
        float r = Math.min(w, h) * 0.33f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AgentTheme.dp(getContext(), 1.6f));
        paint.setColor(0x88479FEA);
        canvas.drawCircle(cx, cy, r * 1.22f, paint);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStrokeWidth(AgentTheme.dp(getContext(), 2.2f));
        paint.setColor(AgentTheme.CYAN);
        canvas.drawLine(cx, cy, w * 0.88f, h * 0.24f, paint);
        canvas.drawLine(cx, cy, w * 0.90f, h * 0.72f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(AgentTheme.BLUE);
        canvas.drawCircle(cx, cy, r * 0.34f, paint);
        paint.setColor(AgentTheme.CYAN);
        canvas.drawCircle(w * 0.88f, h * 0.24f, AgentTheme.dp(getContext(), 2.8f), paint);
        canvas.drawCircle(w * 0.90f, h * 0.72f, AgentTheme.dp(getContext(), 2.8f), paint);
    }
}
