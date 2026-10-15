package com.agentreach.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Animated reach-map used on the home and loading screens. */
public final class SignalView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;
    private boolean active = true;
    private ValueAnimator animator;

    public SignalView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Agent Reach signal map");
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                phase = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
    }

    public void setActive(boolean value) {
        active = value;
        if (isAttachedToWindow()) {
            if (active && !animator.isStarted()) animator.start();
            if (!active && animator.isStarted()) animator.cancel();
        }
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float cx = width * 0.38f;
        float cy = height * 0.53f;
        float radius = Math.min(width, height) * 0.15f;

        glow.setShader(new RadialGradient(cx, cy, radius * 3.4f,
                new int[]{0x4420A9FF, 0x180A84FF, 0x00070A14},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 3.4f, glow);
        glow.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AgentTheme.dp(getContext(), 1));
        paint.setColor(0x332E6EB6);
        for (int index = 1; index <= 3; index++) canvas.drawCircle(cx, cy, radius * index, paint);

        float[][] nodes = {
                {width * 0.10f, height * 0.25f}, {width * 0.12f, height * 0.78f},
                {width * 0.69f, height * 0.18f}, {width * 0.88f, height * 0.48f},
                {width * 0.74f, height * 0.82f}
        };
        for (int index = 0; index < nodes.length; index++) {
            float nx = nodes[index][0];
            float ny = nodes[index][1];
            paint.setStrokeWidth(AgentTheme.dp(getContext(), index == 3 ? 2.2f : 1.2f));
            paint.setColor(index == 3 ? 0xCC14C8FF : 0x663A8FE8);
            Path path = new Path();
            path.moveTo(cx, cy);
            float bend = (index % 2 == 0 ? -1 : 1) * radius * 0.45f;
            path.quadTo((cx + nx) / 2f, (cy + ny) / 2f + bend, nx, ny);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(index == 3 ? AgentTheme.CYAN : AgentTheme.BLUE);
            float pulse = index == 3 ? 5f + (float) Math.sin(phase * Math.PI * 2) * 2f : 3.5f;
            canvas.drawCircle(nx, ny, AgentTheme.dp(getContext(), pulse), paint);
            paint.setStyle(Paint.Style.STROKE);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(AgentTheme.BLUE);
        canvas.drawCircle(cx, cy, radius * 0.42f, paint);
        paint.setColor(AgentTheme.CYAN);
        canvas.drawCircle(cx, cy, radius * 0.18f, paint);

        if (active) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AgentTheme.dp(getContext(), 2));
            paint.setColor(0x9914C8FF);
            canvas.drawCircle(cx, cy, radius * (0.6f + phase * 2.5f), paint);
        }
    }
}
