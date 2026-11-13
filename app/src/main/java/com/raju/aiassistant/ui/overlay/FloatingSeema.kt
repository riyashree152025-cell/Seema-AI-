package com.raju.aiassistant.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.raju.aiassistant.MainActivity
import com.raju.aiassistant.RajuApp
import com.raju.aiassistant.voice.SeemaWakeService

/**
 * Floating Seema: when the user leaves the app (Home button, another app), a glass card rises from the
 * bottom of the screen over whatever is open — her eye, what she is doing, and 🎤 बोलो / रोको / खोलो —
 * and after a few quiet seconds folds into a small eye bubble at the screen's edge (drag it anywhere;
 * tap it to open the card). It opens again by itself while she listens, thinks or speaks ("Hey Seema"
 * in the background) or waits for a "हाँ / नहीं". Coming back to the app removes it.
 *
 * Shown only with Android's "Display over other apps" permission, while the microphone service runs and
 * Seema is ON, and if the user has not switched it off (Settings → Listening). Never on the lock screen
 * (Android does not allow it). All calls on the main thread.
 */
object FloatingSeema {
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var snap: ValueAnimator? = null
    private val showSoon = Runnable { appContext?.let { showNow(it) } }
    private var appContext: Context? = null

    internal val mode = mutableStateOf(FloatMode.CARD)

    /** Where the bubble rests between appearances (this session). */
    private var bubbleX = -1
    private var bubbleY = -1

    val showing: Boolean get() = view != null

    /**
     * The app's screen went to the background. [appLocked]: the app lock has not been passed (then
     * nothing is shown). Waits a moment, so a quick trip (a permission page) does not flash it.
     */
    fun onAppHidden(context: Context, appLocked: Boolean) {
        val c = (context.applicationContext as? RajuApp)?.container ?: return
        val s = c.currentSettings.value
        if (!mayShowFloating(s.floatingPopup, s.assistantPaused, canDraw(context), SeemaWakeService.running, appLocked)) return
        appContext = context.applicationContext
        main.removeCallbacks(showSoon)
        main.postDelayed(showSoon, SHOW_DELAY_MS)
    }

    /** The app is on screen again (or was closed): the popup goes away. */
    fun onAppShown() = hide()

    fun hide() {
        main.removeCallbacks(showSoon)
        snap?.cancel()
        snap = null
        val v = view ?: return
        view = null
        runCatching { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v) }
        owner?.destroy()
        owner = null
        params = null
    }

    fun canDraw(context: Context): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun showNow(context: Context) {
        if (view != null) return
        val c = (context.applicationContext as? RajuApp)?.container ?: return
        // Checked again: something may have changed during the short wait.
        if (!canDraw(context) || !SeemaWakeService.running || c.currentSettings.value.assistantPaused) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val o = OverlayOwner().also { it.start() }
        mode.value = FloatMode.CARD
        // Read once, outside the composition: with the app lock on, the popup shows no words.
        val hideWords = c.currentSettings.value.appLock
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                FloatingSeemaContent(
                    container = c,
                    mode = mode.value,
                    hideWords = hideWords,
                    onFold = { setMode(FloatMode.BUBBLE) },
                    onOpenCard = { setMode(FloatMode.CARD) },
                    onClose = { hide() },
                    onOpenApp = { openApp(context) },
                    onDrag = { dx, dy -> dragBy(dx, dy) },
                    onDragEnd = { restBubble() },
                )
            }
        }
        val p = cardParams(context)
        val added = runCatching { wm.addView(v, p) }
        if (added.isFailure) {
            // The permission was taken back, or the phone refuses overlays right now: nothing to clean up but the owner.
            o.destroy()
            return
        }
        view = v
        owner = o
        params = p
    }

    private fun setMode(m: FloatMode) {
        val v = view ?: return
        val ctx = v.context
        snap?.cancel()
        val p = if (m == FloatMode.CARD) cardParams(ctx) else bubbleParams(ctx)
        mode.value = m
        params = p
        runCatching { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, p) }
    }

    private fun dragBy(dx: Float, dy: Float) {
        val v = view ?: return
        val p = params ?: return
        if (mode.value != FloatMode.BUBBLE) return
        snap?.cancel()
        p.x += dx.toInt()
        p.y += dy.toInt()
        bubbleX = p.x
        bubbleY = p.y
        runCatching { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, p) }
    }

    /** After a drag the bubble glides to the nearer side edge, inside the screen. */
    private fun restBubble() {
        val v = view ?: return
        val p = params ?: return
        val (sw, sh) = screenSize(v.context)
        val margin = dp(v.context, 8)
        val w = v.width.takeIf { it > 0 } ?: dp(v.context, BUBBLE_DP)
        val h = v.height.takeIf { it > 0 } ?: dp(v.context, BUBBLE_DP)
        val toX = bubbleRestX(p.x + w / 2, sw, w, margin)
        val toY = bubbleRestY(p.y, h, dp(v.context, 48), sh - dp(v.context, 64))
        val fromX = p.x
        val fromY = p.y
        snap?.cancel()
        snap = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { a ->
                val k = a.animatedValue as Float
                val cur = params ?: return@addUpdateListener
                val vv = view ?: return@addUpdateListener
                cur.x = (fromX + (toX - fromX) * k).toInt()
                cur.y = (fromY + (toY - fromY) * k).toInt()
                bubbleX = cur.x
                bubbleY = cur.y
                runCatching { (vv.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(vv, cur) }
            }
            start()
        }
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private fun baseParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        // Never takes the keyboard or the touches outside it: the app below works as usual.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    )

    private fun cardParams(context: Context) = baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = navigationBarHeight(context) + dp(context, 10)
    }

    private fun bubbleParams(context: Context) = baseParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.TOP or Gravity.START
        val (sw, sh) = screenSize(context)
        val size = dp(context, BUBBLE_DP)
        if (bubbleX < 0) {
            bubbleX = sw - size - dp(context, 8)
            bubbleY = (sh * 0.55f).toInt()
        }
        x = bubbleX
        y = bubbleY
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = context.resources.displayMetrics
            m.widthPixels to m.heightPixels
        }
    }

    private fun navigationBarHeight(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return runCatching {
                wm.currentWindowMetrics.windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            }.getOrDefault(dp(context, 48))
        }
        return dp(context, 48)
    }

    private fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private const val SHOW_DELAY_MS = 450L
    internal const val BUBBLE_DP = 72
}

/** The lifecycle a ComposeView needs when it lives in its own window instead of an Activity. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }
}
