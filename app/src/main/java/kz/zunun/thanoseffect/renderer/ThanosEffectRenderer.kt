package kz.zunun.thanoseffect.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.opengl.GLES10
import android.opengl.GLES11
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.View
import androidx.annotation.Size
import kz.zunun.thanoseffect.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.Volatile

/**
 * An implementation of GLSurfaceView.Renderer used for drawing a dust ("Thanos") effect on disappearing Views.
 *
 * @author Alexander Yuzefovich
 * */
class ThanosEffectRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var duration = defaultAnimationDuration
    private var particleSize = defaultParticleSize
    private var particlesProgramId = 0
    private var aParticleIndex = 0
    private val renderInfos = ConcurrentLinkedQueue<RenderInfo>()


    override fun onSurfaceCreated(arg0: GL10, arg1: EGLConfig) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val particlesVertexShaderId =
            createShader(context, GLES20.GL_VERTEX_SHADER, R.raw.particles_vert)
        val particlesFragmentShaderId =
            createShader(context, GLES20.GL_FRAGMENT_SHADER, R.raw.particles_frag)
        particlesProgramId =
            createProgram(particlesVertexShaderId, particlesFragmentShaderId)
        aParticleIndex = GLES20.glGetAttribLocation(particlesProgramId, "a_ParticleIndex")
    }

    override fun onSurfaceChanged(arg0: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(particlesProgramId)
        glUniform1f("u_ViewportWidth", width.toFloat())
        glUniform1f("u_ViewportHeight", height.toFloat())
    }

    override fun onDrawFrame(arg0: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (renderInfos.isEmpty()) {
            return
        }
        GLES20.glUseProgram(particlesProgramId)
        glUniform1f("u_AnimationDuration", duration.toFloat())
        glUniform1f("u_ParticleSize", particleSize.toFloat())
        val currentTime = System.currentTimeMillis()
        val iterator = renderInfos.iterator()
        while (iterator.hasNext()) {
            val renderInfo = iterator.next()
            if (!renderInfo.canBeRendered) {
                iterator.remove()
                continue
            }
            if (!renderInfo.isReadyForRender) {
                continue
            }
            renderInfo.loadTextureIfNeeded()
            val isFrameDrawn = drawFrame(renderInfo, currentTime)
            if (!isFrameDrawn) {
                renderInfo.recycle()
                iterator.remove()
            }
        }
    }

    /**
     * @return true if frame was successfully drawn or false if not. False means that passed RenderInfo
     * is not needed anymore and can be recycled.
     */
    private fun drawFrame(renderInfo: RenderInfo, currentTime: Long): Boolean {
        if (renderInfo.animationStartTime == -1L) {
            renderInfo.animationStartTime = System.currentTimeMillis()
        }
        val elapsedTime = currentTime - renderInfo.animationStartTime
        if (elapsedTime > duration) {
            return false
        }
        val uTexture = GLES20.glGetUniformLocation(particlesProgramId, "u_Texture")
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES10.glBindTexture(GL10.GL_TEXTURE_2D, renderInfo.textureId)
        glUniform1f("u_ElapsedTime", elapsedTime.toFloat())
        glUniform1f("u_TextureWidth", renderInfo.columnCount.toFloat())
        glUniform1f("u_TextureHeight", renderInfo.rowCount.toFloat())
        glUniform1f("u_TextureLeft", renderInfo.textureLeft)
        glUniform1f("u_TextureTop", renderInfo.textureTop)
        GLES20.glVertexAttribPointer(
            aParticleIndex,
            1,
            GL10.GL_FLOAT,
            false,
            0,
            renderInfo.particlesIndicesBuffer
        )
        GLES20.glEnableVertexAttribArray(aParticleIndex)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, renderInfo.columnCount * renderInfo.rowCount)
        GLES20.glDisableVertexAttribArray(aParticleIndex)
        return true
    }

    fun composeView(view: View, @Size(2) offset: IntArray?) {
        val renderInfo = RenderInfo(particleSize)
        renderInfos.add(renderInfo)
        renderInfo.composeView(view, offset)
    }

    private fun glUniform1f(name: String, param: Float) {
        val location = GLES20.glGetUniformLocation(particlesProgramId, name)
        GLES20.glUniform1f(location, param)
    }

    private class RenderInfo(private val particleSize: Int) {
        var columnCount = 0
        var rowCount = 0
        var textureLeft = 0f
        var textureTop = 0f
        private var sourceBitmap: Bitmap? = null
        var textureId = 0
        var particlesIndicesBuffer: FloatBuffer? = null
        var animationStartTime: Long = -1

        @Volatile
        var canBeRendered = true

        @Volatile
        var isReadyForRender = false

        private var isTextureLoaded = false

        fun loadTextureIfNeeded() {
            if (isTextureLoaded) {
                return
            }
            val sourceBitmap = sourceBitmap
            check(!(sourceBitmap == null || sourceBitmap.isRecycled)) { "Source bitmap can't be used: null or recycled." }
            val textureHandle = IntArray(1)
            GLES20.glGenTextures(1, textureHandle, 0)
            if (textureHandle[0] != 0) {
                GLES10.glBindTexture(GL10.GL_TEXTURE_2D, textureHandle[0])
                GLES11.glTexParameteri(
                    GL10.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST
                )
                GLES11.glTexParameteri(
                    GL10.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_NEAREST
                )
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, sourceBitmap, 0)
                this.sourceBitmap = null
            }
            if (textureHandle[0] == 0) {
                throw RuntimeException("Error loading texture.")
            }
            textureId = textureHandle[0]
            isTextureLoaded = true
        }

        fun composeView(view: View, @Size(2) offset: IntArray?) {
            if (isReadyForRender) {
                // Only one-shot usage is allowed.
                return
            }
            val viewVisibleRect = Rect()
            val isViewVisible = view.getLocalVisibleRect(viewVisibleRect)
            if (!isViewVisible || viewVisibleRect.width() == 0 || viewVisibleRect.height() == 0) {
                canBeRendered = false
                return
            }
            val viewLocation = IntArray(2)
            view.getLocationOnScreen(viewLocation)
            columnCount = viewVisibleRect.width() / particleSize
            rowCount = viewVisibleRect.height() / particleSize
            textureLeft = (viewLocation[0] + viewVisibleRect.left).toFloat()
            textureTop = (viewLocation[1] + viewVisibleRect.top).toFloat()
            if (offset != null && offset.size >= 2) {
                textureLeft += offset[0].toFloat()
                textureTop += offset[1].toFloat()
            }
            val executorService = Executors.newFixedThreadPool(2)
            executorService.submit {
                val viewBitmap = Bitmap.createBitmap(
                    viewVisibleRect.width(),
                    viewVisibleRect.height(),
                    Bitmap.Config.ARGB_8888
                )
                val c = Canvas(viewBitmap)
                c.translate(-viewVisibleRect.left.toFloat(), -viewVisibleRect.top.toFloat())
                view.draw(c)
                sourceBitmap = viewBitmap
            }
            executorService.submit {
                val particlesCount = columnCount * rowCount
                val particlesIndices = FloatArray(particlesCount)
                for (i in 0 until particlesCount) {
                    particlesIndices[i] = i.toFloat()
                }
                particlesIndicesBuffer = createFloatBuffer(particlesIndicesBuffer, particlesIndices)
            }
            executorService.shutdown()
            try {
                val terminated = executorService.awaitTermination(1, TimeUnit.MINUTES)
                if (!terminated) {
                    executorService.shutdownNow()
                }
            } catch (ie: InterruptedException) {
                ie.printStackTrace()
            }
            isReadyForRender = true
        }

        fun recycle() {
            val textureHandle = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textureHandle, 0)
        }

        private fun createFloatBuffer(
            existingBuffer: FloatBuffer?,
            values: FloatArray,
        ): FloatBuffer {
            val buffer: FloatBuffer =
                if (existingBuffer != null && existingBuffer.capacity() == values.size) {
                    existingBuffer
                } else {
                    ByteBuffer
                        .allocateDirect(values.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                }
            buffer.put(values)
            buffer.position(0)
            return buffer
        }
    }

    companion object {
        const val defaultAnimationDuration = 1800L
        private const val defaultParticleSize = 1
    }
}
