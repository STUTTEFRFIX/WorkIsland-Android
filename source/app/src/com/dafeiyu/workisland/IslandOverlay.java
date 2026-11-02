package com.dafeiyu.workisland;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import java.lang.ref.WeakReference;

/**
 * 没有流体云 / 灵动岛的设备上的兜底胶囊。
 *
 * <p>一条半透明的短胶囊：左边是当前 bot，中间是标题和动作，右边是计时和状态点，
 * 底部按 TodoWrite 清单分段。窗口带 {@code FLAG_NOT_TOUCHABLE}，触摸完全穿透，
 * 只负责“看”，不挡下面任何操作；点通知仍然可以打开应用。
 *
 * <p>字号、高度、垂直位置、不透明度来自 {@link Prefs#capsuleStyle}；设置页用同一个
 * {@link CapsuleView} 做实时预览。应用在前台、锁屏或息屏时隐藏。
 *
 * <p>实现思路参考了 CurrentMusicX 的桌面岛（GPL-3.0），但没有复制其代码：
 * 这里用 Java + Canvas 重写，尺寸、透明度和交互都按工作岛的状态模型重新设计。
 */
final class IslandOverlay {
    private static final String TAG = "WorkIsland";

    /** 应用在前台时不盖住自己的界面；MainActivity 在 onResume/onPause 里更新。 */
    private static volatile boolean appVisible = false;
    private static WeakReference<IslandOverlay> active = new WeakReference<>(null);

    private final Context context;
    private final Context visual;
    private final WindowManager windowManager;
    private final PowerManager power;
    private final KeyguardManager keyguard;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CapsuleView view;

    private WindowManager.LayoutParams params;
    private boolean attached = false;
    private boolean hasContent = false;
    private boolean foreground = false;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            applyVisibility();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!hasContent || view.isDone() || !shouldShow()) return;
            view.invalidate();
            handler.postDelayed(this, 1000);
        }
    };

    IslandOverlay(Context source) {
        context = source.getApplicationContext();
        // WindowManager 类型为 OVERLAY 的窗口上下文会带上正确的密度/主题；
        // 直接用 Activity/Service 的 context 在部分机型上会拿到错误尺寸。
        Context window = context;
        try {
            window = context.createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        } catch (RuntimeException failure) {
            Log.w(TAG, "window context unavailable, using app context", failure);
        }
        visual = window;
        windowManager = visual.getSystemService(WindowManager.class);
        power = context.getSystemService(PowerManager.class);
        keyguard = context.getSystemService(KeyguardManager.class);
        view = new CapsuleView(visual);
        foreground = appVisible;
        active = new WeakReference<>(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        } catch (RuntimeException failure) {
            Log.w(TAG, "cannot watch screen state", failure);
        }
    }

    /** MainActivity 可见性变化时调用；没有活动的胶囊时是空操作。 */
    static void onAppVisibilityChanged(boolean visible) {
        appVisible = visible;
        IslandOverlay overlay = active.get();
        if (overlay != null) overlay.setAppVisible(visible);
    }

    private void setAppVisible(boolean visible) {
        foreground = visible;
        applyVisibility();
    }

    void show(String title, String text, long since, boolean done, String state, String face,
              int todoDone, int todoTotal, int anim) {
        applyStyle();
        view.setContent(title, text, since, done, state, face, todoDone, todoTotal, anim);
        hasContent = true;
        applyVisibility();
    }

    /** 交接时先淡出；窗口本身留着，下一轮 show 不需要重新 addView。 */
    void hide() {
        hasContent = false;
        stopTicker();
        applyVisibility();
    }

    /** 旋转 / 分屏 / 设置变化后重算窗口尺寸、位置和透明度。 */
    void refreshLayout() {
        applyStyle();
    }

    void remove() {
        hasContent = false;
        stopTicker();
        view.animate().cancel();
        if (attached) {
            try {
                windowManager.removeViewImmediate(view);
            } catch (RuntimeException failure) {
                Log.w(TAG, "cannot remove capsule", failure);
            }
            attached = false;
        }
        try {
            context.unregisterReceiver(screen);
        } catch (RuntimeException ignored) {
            // 服务销毁时 receiver 可能已经随进程一起没了。
        }
        if (active.get() == this) active.clear();
    }

    private void applyVisibility() {
        if (hasContent && !attached && Settings.canDrawOverlays(context)) attach();
        if (!attached) {
            stopTicker();
            return;
        }
        if (shouldShow()) {
            fadeIn();
            startTicker();
        } else {
            stopTicker();
            fadeOut();
        }
    }

    private boolean shouldShow() {
        return attached && hasContent && !foreground
                && power.isInteractive() && !keyguard.isKeyguardLocked();
    }

    /** 读取当前样式并同步到视图和窗口；窗口尺寸/位置/alpha 只在变化时更新。 */
    private void applyStyle() {
        CapsuleStyle style = Prefs.capsuleStyle(context);
        view.setStyle(style);
        if (!attached || params == null) return;

        WindowMetrics metrics = windowManager.getMaximumWindowMetrics();
        Rect bounds = metrics.getBounds();
        Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int width = Math.max(dp(120),
                Math.min(dp(style.maxWidthDp() + 30f), bounds.width() - dp(32)));
        int height = dp(style.heightDp);
        int base = bars.top + dp(4);
        int available = Math.max(0, bounds.height() - bars.top - bars.bottom - height - dp(8));
        int top = base + Math.round(available * style.y);
        float alpha = Math.max(.35f, Math.min(style.opacity, maximumTouchThroughAlpha()));
        if (params.width == width && params.height == height
                && params.y == top && params.alpha == alpha) return;
        params.width = width;
        params.height = height;
        params.y = top;
        params.alpha = alpha;
        try {
            windowManager.updateViewLayout(view, params);
        } catch (RuntimeException failure) {
            Log.w(TAG, "cannot relayout capsule", failure);
        }
    }

    private void attach() {
        if (attached) return;
        CapsuleStyle style = Prefs.capsuleStyle(context);
        view.setStyle(style);
        DisplayMetrics metrics = visual.getResources().getDisplayMetrics();
        int width = Math.max(dp(120),
                Math.min(dp(style.maxWidthDp() + 30f), metrics.widthPixels - dp(32)));
        int height = dp(style.heightDp);
        params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_TOUCHABLE 是重点：胶囊只负责显示，点击、滑动全部穿透到下面的应用。
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 0;
        params.setTitle("工作岛胶囊");
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.alpha = Math.max(.35f, Math.min(style.opacity, maximumTouchThroughAlpha()));
        view.setVisibility(View.GONE);
        view.setAlpha(0f);
        try {
            windowManager.addView(view, params);
            attached = true;
            applyStyle();
        } catch (RuntimeException failure) {
            Log.w(TAG, "cannot add capsule overlay", failure);
            params = null;
        }
    }

    /**
     * Android 12+ 会把“不接收触摸且不透明度 > 0.8”的悬浮窗当作遮挡。
     * 窗口 alpha 压到系统上限以内，才能保证触摸真的穿透到下面的应用。
     */
    private float maximumTouchThroughAlpha() {
        float maximum = .8f;
        try {
            InputManager input = visual.getSystemService(InputManager.class);
            if (input != null) maximum = input.getMaximumObscuringOpacityForTouch();
        } catch (RuntimeException ignored) {
            // 个别 ROM 没有实现这个查询，用 AOSP 的默认 0.8。
        }
        return Math.min(.8f, maximum);
    }

    private void fadeIn() {
        if (view.getVisibility() == View.VISIBLE && view.getAlpha() >= .99f) return;
        view.animate().cancel();
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setScaleX(.94f);
            view.setScaleY(.94f);
            view.setTranslationY(-dp(4));
            view.setVisibility(View.VISIBLE);
        }
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(180).start();
    }

    private void fadeOut() {
        if (view.getVisibility() != View.VISIBLE) return;
        view.animate().cancel();
        view.animate().alpha(0f).scaleX(.94f).scaleY(.94f).translationY(-dp(4))
                .setDuration(150)
                .withEndAction(() -> {
                    if (!shouldShow()) view.setVisibility(View.GONE);
                }).start();
    }

    private void startTicker() {
        handler.removeCallbacks(tick);
        if (!view.isDone()) handler.postDelayed(tick, 1000);
    }

    private void stopTicker() {
        handler.removeCallbacks(tick);
    }

    private int dp(float value) {
        return Math.round(visual.getResources().getDisplayMetrics().density * value);
    }
}
