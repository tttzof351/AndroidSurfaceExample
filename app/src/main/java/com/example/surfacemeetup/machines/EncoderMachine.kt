package com.example.surfacemeetup.machines

import android.opengl.EGLContext
import android.opengl.EGLSurface
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.widget.Toast
import com.example.surfacemeetup.grafika.EglCore
import com.example.surfacemeetup.helpers.ContextHelper
import com.example.surfacemeetup.helpers.EncoderHelper
import com.example.surfacemeetup.helpers.OpenGLScene
import com.example.surfacemeetup.helpers.ThreadUtils
import com.example.surfacemeetup.machines.EncoderAction.*
import com.example.surfacemeetup.machines.EncoderState.*
import com.example.surfacemeetup.utils.SimpleProducer
import java.io.File

class EncoderMachine : StateMachine<EncoderState, EncoderAction> {
    @Volatile
    override var state: EncoderState = WaitStartRecord()

    private val handlerThread = HandlerThread("encoder handler thread")
    private val handler: Handler
    private val filePath =
        "${Environment.getExternalStorageDirectory().path}/mediacoder-record.mp4"

    val drawingCaller = object: EncoderDrawingCaller {
        override fun invoke() {
            if (state is RecordAvailable) send(AddFrame)
        }
    }

    var toggleRecordCallback: ToggleRecordCallback? = null

    init {
        handlerThread.start()
        handler = object: Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                transition(msg.obj as EncoderAction)
            }
        }
    }

    override fun send(action: EncoderAction) {
        handler.sendMessage(Message.obtain(handler, 0, action))
    }

    override fun transition(action: EncoderAction) {
        val state = this.state

        when {
            state is WaitStartRecord && action is Start -> {
                val mh = state.encoderHolder.copy(surfaceProducer = action.surfaceProducer)
                this.state = WaitGLContext(mh)
                fetchContext(action)
            }
            state is WaitGLContext && action is GLContextReady -> {
                val mh = prepareEncoder(state, action)
                this.state = RecordAvailable(mh)
            }
            state is RecordAvailable && action is AddFrame -> {
                addFrame(state)
            }
            state !is WaitStartRecord && action is Stop -> {
                stopRecord(state)
                this.state = WaitStartRecord()
            }
        }

        notifyChangeActiveState(state)
    }

    fun isActive() = state !is WaitStartRecord

    private fun fetchContext(action: Start) {
        action.surfaceProducer.consume { triplet ->
            val (context, smallTextureId, fullscreenTextureId) = triplet
            send(
                GLContextReady(
                    context,
                    smallTextureId,
                    fullscreenTextureId
                )
            )
        }
    }

    private fun prepareEncoder(
        state: WaitGLContext,
        action: GLContextReady
    ): EncoderHolder {
        val sharedContext = action.sharedEglContext
        val drawableTextureId = action.drawableTextureId
        val cameraTextureId = action.cameraTextureId

        val outputFile = File(filePath)

        if (outputFile.exists()) {
            outputFile.delete()
        }

        val encoder = EncoderHelper(
            outputFile = outputFile,
            encoderWidth = 720,
            encoderHeight = (720f * OpenGLScene.ASPECT_RATIO).toInt()
        )

        val eglCore = EglCore(sharedContext, EglCore.FLAG_RECORDABLE)
        val eglSurface = eglCore.createWindowSurface(encoder.surface)
        eglCore.makeCurrent(eglSurface)

        val openGLScene = OpenGLScene(
            encoder.encoderWidth,
            encoder.encoderHeight,
            fullscreenTextureId = cameraTextureId,
            smallTextureId = drawableTextureId
        )

        return state.encoderHolder.copy(
            eglCore = eglCore,
            eglSurface = eglSurface,
            openGLScene = openGLScene,
            encoder = encoder,
            drawableTextureId = drawableTextureId,
            cameraTextureId = cameraTextureId
        )
    }

    private fun addFrame(state: RecordAvailable) {
        val encoder = state.encoderHolder.encoder ?: return
        val openGLScene = state.encoderHolder.openGLScene ?: return
        val eglCore = state.encoderHolder.eglCore ?: return
        val eglSurface = state.encoderHolder.eglSurface ?: return

        encoder.drainEncoder(false)
        openGLScene.updateFrame()
        eglCore.swapBuffers(eglSurface)
    }

    private fun stopRecord(state: EncoderState) {
        state.encoderHolder.encoder?.release()
        state.encoderHolder.openGLScene?.release()

        showStopToast()
    }

    private fun notifyChangeActiveState(oldState: State) {
        when {
            oldState is WaitStartRecord && this.state !is WaitStartRecord -> {
                ThreadUtils.runOnUI {
                    toggleRecordCallback?.invoke(true)
                }
            }

            oldState !is WaitStartRecord && this.state is WaitStartRecord -> {
                ThreadUtils.runOnUI {
                    toggleRecordCallback?.invoke(false)
                }
            }
        }
    }

    private fun showStopToast() {
        if (File(filePath).exists()) {
            Toast.makeText(
                ContextHelper.appContext,
                "Stop record: $filePath",
                Int.MIN_VALUE
            ).show()
        }
    }
}

sealed class EncoderAction: Action {
    class Start(val surfaceProducer: SimpleProducer<EncoderSurfaceParams>): EncoderAction()
    class GLContextReady(
        val sharedEglContext: EGLContext,
        val drawableTextureId: Int,
        val cameraTextureId: Int
    ): EncoderAction()
    object AddFrame: EncoderAction()
    object Stop: EncoderAction()
}

sealed class EncoderState(val encoderHolder: EncoderHolder) : State {
    class WaitStartRecord(): EncoderState(EncoderHolder())
    class WaitGLContext(mh: EncoderHolder): EncoderState(mh)
    class RecordAvailable(mh: EncoderHolder): EncoderState(mh)
}

data class EncoderHolder(
    val surfaceProducer: SimpleProducer<EncoderSurfaceParams>? = null,
    val eglCore: EglCore? = null,
    val openGLScene: OpenGLScene? = null,
    val encoder: EncoderHelper? = null,
    val eglSurface: EGLSurface? = null,
    val drawableTextureId: Int = 0,
    val cameraTextureId: Int = 0
)

typealias ToggleRecordCallback = (isActive: Boolean) -> Unit

typealias EncoderDrawingCaller = () -> Unit

typealias EncoderSurfaceParams = Triple<EGLContext, Int, Int>