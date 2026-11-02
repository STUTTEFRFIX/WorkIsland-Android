package com.dafeiyu.workisland;

/**
 * 悬浮胶囊的可调样式。构造时统一夹紧，设置页和悬浮窗都不需要再防越界。
 *
 * <p>字号决定整体大小（图标、间距、最大宽度都跟着放大/缩小）；
 * 高度独立控制胶囊高矮；y 是状态栏以下可用高度的比例，0 = 紧贴状态栏下方。
 * 不透明度上限 0.8，是为了 Android 12+ 的触摸穿透规则（见 IslandOverlay.attach）。
 */
final class CapsuleStyle {
    static final float DEFAULT_TEXT_SP = 10f;
    static final float DEFAULT_HEIGHT_DP = 40f;
    static final float DEFAULT_Y = 0f;
    static final float DEFAULT_OPACITY = .78f;

    final float textSp;
    final float heightDp;
    final float y;
    final float opacity;

    CapsuleStyle(float textSp, float heightDp, float y, float opacity) {
        this.textSp = clamp(textSp, 9f, 16f);
        this.heightDp = clamp(heightDp, 32f, 64f);
        this.y = clamp(y, 0f, .8f);
        this.opacity = clamp(opacity, .35f, .8f);
    }

    static CapsuleStyle defaults() {
        return new CapsuleStyle(DEFAULT_TEXT_SP, DEFAULT_HEIGHT_DP, DEFAULT_Y, DEFAULT_OPACITY);
    }

    boolean same(CapsuleStyle other) {
        return other != null && textSp == other.textSp && heightDp == other.heightDp
                && y == other.y && opacity == other.opacity;
    }

    /** 高度太小时自动压字号，避免两行字被胶囊裁掉。 */
    float effectiveTextSp() {
        return Math.min(textSp, heightDp * .285f);
    }

    float maxWidthDp() {
        return Math.min(280f, 200f + (textSp - 9f) * 10f);
    }

    float minWidthDp() {
        return 100f + (textSp - 9f) * 4f;
    }

    float iconDp() {
        return 24f + (textSp - 9f) * 2f;
    }

    float iconLeftDp() {
        return 6f + (textSp - 9f) * .4f;
    }

    float gapDp() {
        return 7f + (textSp - 9f) * .5f;
    }

    float rightPadDp() {
        return 10f + (textSp - 9f) * .5f;
    }

    private static float clamp(float value, float lo, float hi) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return lo;
        return Math.max(lo, Math.min(hi, value));
    }
}
