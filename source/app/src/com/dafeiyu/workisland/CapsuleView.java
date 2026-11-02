package com.dafeiyu.workisland;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

/**
 * 半透明、不接收触摸的胶囊视图。悬浮窗和应用内预览共用这一份绘制代码，
 * 所以设置页看到的就是岛上的样子。
 *
 * <p>字号 / 高度 / 垂直位置 / 不透明度由 {@link CapsuleStyle} 控制；
 * 视图本身只负责画，不持有 WindowManager。
 */
final class CapsuleView extends View {
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segment = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private CapsuleStyle style = CapsuleStyle.defaults();
    private String title = "正在干活";
    private String text = "";
    private String state = "working";
    private String face = "open";
    private long since = System.currentTimeMillis();
    private boolean done = false;
    private int todoDone = 0;
    private int todoTotal = 0;
    private int anim = 0;
    private int accent = 0xFFD97757;
    private Bitmap bot;
    private String botKey = "";

    private float pillWidth = -1f;
    private ValueAnimator widthAnimator;

    CapsuleView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setContentDescription("工作岛胶囊");
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titlePaint.setColor(0xFFFAF9F5);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setColor(0xB8FAF9F5);
        timePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        timePaint.setColor(0x99FAF9F5);
        timePaint.setTextAlign(Paint.Align.RIGHT);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1));
        segment.setStyle(Paint.Style.FILL);
        dot.setStyle(Paint.Style.FILL);
        applyTextSizes();
    }

    void setStyle(CapsuleStyle next) {
        if (next == null || next.same(style)) return;
        style = next;
        applyTextSizes();
        if (widthAnimator != null) {
            widthAnimator.cancel();
            widthAnimator = null;
        }
        pillWidth = getWidth() > 0 ? measuredWidth() : -1f;
        botKey = "";
        requestLayout();
        invalidate();
    }

    void setContent(String title, String text, long since, boolean done, String state,
                    String face, int todoDone, int todoTotal, int anim) {
        this.title = title == null || title.isEmpty()
                ? (done ? "任务完成" : "正在干活") : title;
        this.text = text == null ? "" : text;
        this.done = done;
        this.state = state == null || state.isEmpty() ? (done ? "done" : "working") : state;
        this.face = face == null || face.isEmpty() ? (done ? "happy" : "open") : face;
        this.since = since > 0 ? since : System.currentTimeMillis();
        this.todoTotal = Math.max(0, todoTotal);
        this.todoDone = Math.max(0, Math.min(this.todoTotal, todoDone));
        this.anim = Math.abs(anim) % 8;
        this.accent = Notifier.statusColor(this.state);
        String key = this.state + "|" + this.face + "|" + this.anim + "|"
                + Prefs.soul(getContext()) + "|" + Prefs.icon(getContext()) + "|"
                + Math.round(style.iconDp() * 10f);
        if (!key.equals(botKey)) {
            botKey = key;
            bot = Notifier.botBitmap(getContext(), this.state, this.face,
                    Math.max(1, dp(style.iconDp())), -1, this.anim);
        }
        setContentDescription(this.title + "，" + detailText());
        animateWidth();
        invalidate();
    }

    boolean isDone() {
        return done;
    }

    private void applyTextSizes() {
        titlePaint.setTextSize(sp(style.effectiveTextSp()));
        textPaint.setTextSize(sp(Math.max(8f, style.effectiveTextSp() * .82f)));
        timePaint.setTextSize(sp(Math.max(8f, style.effectiveTextSp() * .8f)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 半透明底：能看见下面的内容，又保证白字有足够对比度。
        // 窗口 alpha 还会再乘一次（触摸穿透要求 ≤ 0.8），所以这里留得稍深。
        background.setShader(new LinearGradient(0, 0, 0, h,
                0xE61C1B19, 0xC0090807, Shader.TileMode.CLAMP));
        border.setShader(new LinearGradient(0, 0, 0, h,
                0x30FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP));
        if (pillWidth <= 0) pillWidth = measuredWidth();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int desiredWidth = dp(style.maxWidthDp() + 30f);
        int desiredHeight = dp(style.heightDp);
        setMeasuredDimension(resolveSize(desiredWidth, widthSpec),
                resolveSize(desiredHeight, heightSpec));
    }

    private void animateWidth() {
        float target = measuredWidth();
        if (pillWidth <= 0 || getWidth() == 0) {
            pillWidth = target;
            return;
        }
        if (Math.abs(target - pillWidth) < .5f) return;
        if (widthAnimator != null) widthAnimator.cancel();
        widthAnimator = ValueAnimator.ofFloat(pillWidth, target);
        widthAnimator.setDuration(180);
        widthAnimator.addUpdateListener(animation -> {
            pillWidth = (float) animation.getAnimatedValue();
            invalidate();
        });
        widthAnimator.start();
    }

    private float measuredWidth() {
        float max = getWidth() > 0
                ? Math.min(dp(style.maxWidthDp()), getWidth() - dp(4))
                : dp(style.maxWidthDp());
        float min = Math.min(dp(style.minWidthDp()), max);
        float iconArea = dp(style.iconLeftDp() + style.iconDp() + style.gapDp() + style.rightPadDp());
        float clock = done ? 0f : timePaint.measureText(elapsed(since)) + dp(9);
        float textWidth = Math.max(titlePaint.measureText(title) + clock,
                textPaint.measureText(detailText()));
        float width = iconArea + Math.min(textWidth, Math.max(0f, max - iconArea));
        return Math.max(min, Math.min(max, width));
    }

    private String detailText() {
        String detail = text;
        if (todoTotal > 0) {
            String progress = todoDone + "/" + todoTotal;
            if (!detail.contains("清单 " + progress)) {
                detail = detail.isEmpty() ? "清单 " + progress
                        : detail + " · 清单 " + progress;
            }
        }
        return detail;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float available = Math.max(dp(style.minWidthDp()), w - dp(4));
        if (pillWidth <= 0 || pillWidth > available) pillWidth = measuredWidth();
        float left = (w - pillWidth) / 2f;
        float right = left + pillWidth;
        float radius = h / 2f;
        RectF pill = new RectF(left + 1f, 1f, right - 1f, h - 1f);
        canvas.drawRoundRect(pill, radius, radius, background);
        canvas.drawRoundRect(pill, radius, radius, border);

        float icon = dp(style.iconDp());
        float iconLeft = left + dp(style.iconLeftDp());
        float iconTop = (h - icon) / 2f;
        if (bot != null && !bot.isRecycled()) {
            RectF destination = new RectF(iconLeft, iconTop, iconLeft + icon, iconTop + icon);
            canvas.drawBitmap(bot, null, destination, bitmapPaint);
        }

        float textLeft = iconLeft + icon + dp(style.gapDp());
        float textRight = right - dp(style.rightPadDp());
        float titleBaseline = h * .42f;
        float detailBaseline = h * .73f;
        String time = done ? "" : elapsed(since);
        float timeWidth = time.isEmpty() ? 0f : timePaint.measureText(time);
        float dotRadius = dp(2.5f);
        float block = timeWidth + (time.isEmpty() ? 0f : dp(5)) + dotRadius * 2 + dp(5);

        CharSequence fittedTitle = TextUtils.ellipsize(title, titlePaint,
                Math.max(1f, textRight - textLeft - block), TextUtils.TruncateAt.END);
        canvas.drawText(fittedTitle.toString(), textLeft, titleBaseline, titlePaint);

        CharSequence fittedDetail = TextUtils.ellipsize(detailText(), textPaint,
                Math.max(1f, textRight - textLeft), TextUtils.TruncateAt.END);
        canvas.drawText(fittedDetail.toString(), textLeft, detailBaseline, textPaint);

        dot.setColor(accent);
        float dotRight = textRight - dotRadius;
        canvas.drawCircle(dotRight, h * .40f, dotRadius, dot);
        if (!time.isEmpty()) {
            canvas.drawText(time, dotRight - dotRadius - dp(5), titleBaseline, timePaint);
        }

        if (todoTotal > 0) {
            float barTop = h - dp(4);
            float barBottom = barTop + dp(1.5f);
            float start = textLeft;
            float end = textRight;
            if (todoTotal <= 12) {
                float gap = dp(2);
                float each = (end - start - gap * (todoTotal - 1)) / todoTotal;
                for (int i = 0; i < todoTotal; i++) {
                    float x = start + i * (each + gap);
                    segment.setColor(i < todoDone ? accent : 0x33FFFFFF);
                    canvas.drawRoundRect(x, barTop, x + each, barBottom,
                            dp(1), dp(1), segment);
                }
            } else {
                segment.setColor(0x33FFFFFF);
                canvas.drawRoundRect(start, barTop, end, barBottom, dp(1), dp(1), segment);
                segment.setColor(accent);
                canvas.drawRoundRect(start, barTop,
                        start + (end - start) * todoDone / todoTotal, barBottom,
                        dp(1), dp(1), segment);
            }
        }
    }

    private String elapsed(long since) {
        long seconds = Math.max(0, (System.currentTimeMillis() - since) / 1000);
        if (seconds < 3600) {
            return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
        }
        return String.format(Locale.US, "%d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
