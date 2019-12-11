package com.example.surfaces.machines

import android.graphics.drawable.Drawable
import android.opengl.GLSurfaceView
import com.example.surfaces.helpers.OpenGLScene
import com.example.surfaces.machines.GLSurfaceAction.*
import com.example.surfaces.machines.GLSurfaceState.*
import com.example.surfaces.utils.SimpleProducer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLSurfaceMachine: StateMachine<GLSurfaceState, GLSurfaceAction> {

    override var state: GLSurfaceState = WaitingCreate()

    val fullscreenProducer = SimpleProducer {
        val mh = this@GLSurfaceMachine.state.uiHolder
        mh.openGLScene?.fullscreenTexture
    }

    val encoderProducer = SimpleProducer {
        val mh = this@GLSurfaceMachine.state.uiHolder
        mh.openGLScene?.let {
            EncoderSurfaceParams(
                it.openglContext,
                it.smallTexture.textureId,
                it.fullscreenTexture.textureId
            )
        }
    }

    override fun send(action: GLSurfaceAction) {
        transition(action)
    }

    override fun transition(action: GLSurfaceAction) {
        val state = this.state
        when {
            state is WaitingCreate && action is Create -> {
                val uiMutableHolder = setupGlSurface(state, action)
                this.state = WaitingSurfaceReady(uiMutableHolder)

                this.state.uiHolder.glSurfaceView?.setRenderer(generateRender())
            }
            state is WaitingSurfaceReady && action is SurfaceReady -> {
                val nextMutableHolder = prepareOpenGLScene(state, action) ?: return
                this.state = DrawingAvailable(nextMutableHolder)
            }

            state is DrawingAvailable && action is Draw -> {
                drawFrame(state)
            }

            state !is WaitingCreate && action is Stop -> {
                this.state = pauseGL(state)
            }

            state is WaitingSurfaceReady && action is Start -> {
                resume(state)
            }
        }
    }

    private fun resume(state: GLSurfaceState) {
        state.uiHolder.glSurfaceView?.onResume()
    }

    private fun pauseGL(state: GLSurfaceState): WaitingSurfaceReady {
        state.uiHolder.glSurfaceView?.onPause()
        state.uiHolder.openGLScene?.release()

        return WaitingSurfaceReady(
            state.uiHolder.copy(
                openGLScene = null,
                gl = null
            )
        )
    }

    private fun setupGlSurface(
        state: WaitingCreate,
        action: Create
    ): UIHolder {
        action.glSurfaceView.setEGLContextClientVersion(2)

        return state.uiHolder.copy(
            glSurfaceView = action.glSurfaceView,
            drawable = action.drawable,
            encoderDrawingCaller = action.encoderDrawingCaller
        )
    }

    private fun generateRender(): GLSurfaceView.Renderer {
        return object : GLSurfaceView.Renderer {
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                send(SurfaceReady(width, height, gl))
            }

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            }

            override fun onDrawFrame(gl: GL10?) {
                send(Draw)
            }
        }
    }

    private fun prepareOpenGLScene(
        state: WaitingSurfaceReady,
        action: SurfaceReady
    ): UIHolder? {
        val width = action.surfaceWidth
        val height = action.surfaceHeight
        val drawable = state.uiHolder.drawable

        val openGLScene = OpenGLScene(width, height)

        drawable?.setBounds(
            0,
            0,
            openGLScene.smallTexture.textureWidth,
            openGLScene.smallTexture.textureHeight
        )

        fullscreenProducer.putResult(openGLScene.fullscreenTexture)
        encoderProducer.putResult(
            EncoderSurfaceParams(
                openGLScene.openglContext,
                openGLScene.smallTexture.textureId,
                openGLScene.fullscreenTexture.textureId
            )
        )

        return state.uiHolder.copy(
            openGLScene = openGLScene,
            surfaceWidth = width,
            surfaceHeight = height,
            gl = action.gl
        )
    }

    private fun drawFrame(state: DrawingAvailable) {
        drawDrawable(state)

        state.uiHolder.openGLScene?.updateFrame()
        state.uiHolder.encoderDrawingCaller?.invoke()
    }

    private fun drawDrawable(state: DrawingAvailable) {
        val smallSurface = state.uiHolder.openGLScene?.smallTexture?.surface
        val drawable = state.uiHolder.drawable

        if (smallSurface != null && drawable != null) {
            val bounds = drawable.bounds
            val canvas = smallSurface.lockCanvas(bounds)
            drawable.draw(canvas)
            smallSurface.unlockCanvasAndPost(canvas)
        }
    }

}

sealed class GLSurfaceAction : Action {
    class Create(
        val glSurfaceView: GLSurfaceView,
        val drawable: Drawable,
        val encoderDrawingCaller: EncoderDrawingCaller
    ) : GLSurfaceAction()
    class SurfaceReady(
        val surfaceWidth: Int,
        val surfaceHeight: Int,
        val gl: GL10?
    ) : GLSurfaceAction()
    object Draw : GLSurfaceAction()
    object Stop: GLSurfaceAction()
    object Start: GLSurfaceAction()
}

sealed class GLSurfaceState(val uiHolder: UIHolder) : State {
    class WaitingCreate() : GLSurfaceState(UIHolder())
    class WaitingSurfaceReady(uiHolder: UIHolder) : GLSurfaceState(uiHolder)
    class DrawingAvailable(uiHolder: UIHolder) : GLSurfaceState(uiHolder)
}

data class UIHolder(
    val glSurfaceView: GLSurfaceView? = null,
    val surfaceWidth: Int = 0,
    val surfaceHeight: Int = 0,
    val openGLScene: OpenGLScene? = null,
    val drawable: Drawable? = null,
    val encoderDrawingCaller: EncoderDrawingCaller? = null,
    val gl: GL10? = null
)