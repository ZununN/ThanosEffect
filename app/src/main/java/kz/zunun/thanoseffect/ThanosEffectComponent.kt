package kz.zunun.thanoseffect

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kz.zunun.thanoseffect.renderer.ThanosEffectRenderer
import kz.zunun.thanoseffect.renderer.ThanosEffectRenderer.Companion.defaultAnimationDuration

@Composable
fun ThanosEffectComponent(
    trigger: SharedFlow<Unit>,
    modifier: Modifier = Modifier,
    onAnimateFinish: () -> Unit = {},
    content: @Composable () -> Unit,
) {

    val context = LocalContext.current
    val dustEffectRenderer = remember { ThanosEffectRenderer(context) }

    AndroidView(modifier = modifier, factory = {
        it.createThanosView(trigger, dustEffectRenderer, content, onAnimateFinish)
    })
}


private fun Context.createThanosView(
    trigger: SharedFlow<Unit>,
    dustEffectRenderer: ThanosEffectRenderer,
    content: @Composable () -> Unit,
    onAnimateFinish: () -> Unit,
): View {

    val scope = CoroutineScope(Dispatchers.Main.immediate)
    val root = FrameLayout(this).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                scope.cancel()
            }
        })
    }

    GLSurfaceView(this).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setEGLContextClientVersion(3)
        setZOrderOnTop(true)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        holder.setFormat(PixelFormat.RGBA_8888)
        setRenderer(dustEffectRenderer)
    }.also {
        root.addView(it)
    }


    val composeView = ComposeView(this).apply {
        setContent(content)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    root.addView(composeView)
    root.addView(ComposeView(this))

    scope.launch {
        trigger.collect {
            val contentViewLocation = intArrayOf(0, 0).apply {
                root.getLocationOnScreen(this)
                for (i in indices) {
                    this[i] *= -1
                }
            }
            dustEffectRenderer.composeView(composeView, contentViewLocation)
            Handler(Looper.getMainLooper()).postDelayed(
                { onAnimateFinish() },
                defaultAnimationDuration / 2
            )
            root.removeView(composeView)
        }
    }
    return root
}