package com.example.surfacemeetup.machines

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Size
import android.view.Surface
import android.widget.Toast
import com.example.surfacemeetup.helpers.ContextHelper
import com.example.surfacemeetup.helpers.OpenGLExternalTexture
import com.example.surfacemeetup.helpers.OpenGLScene
import com.example.surfacemeetup.utils.SimpleProducer
import kotlin.math.abs

class CameraMachine() : StateMachine<CameraState, CameraAction> {

    override var state : CameraState = CameraState.WaitingStart()
    private val handlerThread = HandlerThread("camera handler thread")
    private val handler: Handler

    init {
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                transition(msg.obj as CameraAction)
            }
        }
    }

    override fun send(action: CameraAction) {
        handler.sendMessage(Message.obtain(handler, 0, action))
    }

    override fun transition(action: CameraAction) {
        val state = this.state

        when {
            state is CameraState.WaitingStart && action is CameraAction.Start -> {
                val mutableHolder = findCameraId(state, action)
                this.state = CameraState.WaitingOpen(mutableHolder)
                openCamera(mutableHolder.cameraId, mutableHolder.manager)
            }

            state is CameraState.WaitingOpen && action is CameraAction.Open -> {
                val holder = getCameraDevice(state, action) ?: return
                this.state = CameraState.WaitingSurface(holder)
                requestSurface(holder.surfaceProducer)
            }

            state is CameraState.WaitingSurface && action is CameraAction.SurfaceReady -> {
                val holder = createSession(state, action) ?: return
                this.state = CameraState.WaitingSession(holder)
            }

            state is CameraState.WaitingSession && action is CameraAction.SessionReady -> {
                val holder = captureCameraPreview(state, action) ?: return
                this.state = CameraState.StartingPreview(holder)
            }
            action is CameraAction.Stop -> {
                stopCamera(state)
                this.state = CameraState.WaitingStart()
            }
        }
    }

    private fun findCameraId(
        state: CameraState.WaitingStart,
        action: CameraAction.Start
    ): CameraHolder {
        val manager = action.manager

        var resultCameraId: String? = null
        var resultSize: Size? = null

        for (cameraId in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(cameraId)
            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: -1

            if (facing == LENS_FACING_BACK) {
                val confMap = chars.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )

                val sizes = confMap?.getOutputSizes(SurfaceTexture::class.java)
                resultSize = findSize(sizes)

                resultCameraId = cameraId
                break
            }
        }

        return state.cameraHolder.copy(
            manager = action.manager,
            cameraId = resultCameraId,
            surfaceProducer = action.surfaceProducer,
            size = resultSize
        )
    }

    private fun findSize(sizes: Array<out Size>?): Size? {
        return sizes?.find {
            val diffRatio = abs(
                it.width.toFloat() / it.height.toFloat() - OpenGLScene.ASPECT_RATIO
            )
            val res = it.width * it.height
            (res < 700 * 700) && diffRatio < 0.1f
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraId: String?, manager: CameraManager?) {
        cameraId?.let { id ->
            manager?.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onDisconnected(camera: CameraDevice) {
                        send(CameraAction.Stop)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        if (error == ERROR_MAX_CAMERAS_IN_USE) {
                            Toast.makeText(
                                ContextHelper.appContext,
                                "ERROR_MAX_CAMERAS_IN_USE",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        send(CameraAction.Stop)
                    }

                    override fun onOpened(camera: CameraDevice) {
                        send(CameraAction.Open(camera))
                    }
                },
                handler
            )
        }
    }

    private fun getCameraDevice(
        state: CameraState.WaitingOpen,
        action: CameraAction.Open
    ): CameraHolder? {
        val cameraDevice = action.cameraDevice ?: return null
        return state.cameraHolder.copy(cameraDevice = cameraDevice)
    }

    private fun requestSurface(surfaceProducer: SimpleProducer<OpenGLExternalTexture>?) {
        surfaceProducer?.consume { surface ->
            send(CameraAction.SurfaceReady(surface))
        }
    }

    private fun createSession(
        state: CameraState.WaitingSurface,
        action: CameraAction.SurfaceReady
    ): CameraHolder? {
        val cameraDevice = state.cameraHolder.cameraDevice ?: return null
        val texture = action.texture
        val size = state.cameraHolder.size
        if (size != null) {
            texture.rotate =
                if (size.width > size.height) Surface.ROTATION_90
                else Surface.ROTATION_0

            texture.surfaceTexture.setDefaultBufferSize(size.width, size.height)
        }

        val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                send(CameraAction.Stop)
            }

            override fun onConfigured(session: CameraCaptureSession) {
                send(CameraAction.SessionReady(session))
            }

        }

        cameraDevice.createCaptureSession(
            arrayListOf(texture.surface),
            sessionCallback,
            handler
        )

        return state.cameraHolder.copy(texture = texture)
    }

    private fun captureCameraPreview(
        state: CameraState.WaitingSession,
        action: CameraAction.SessionReady
    ): CameraHolder? {
        val cameraDevice = state.cameraHolder.cameraDevice ?: return null
        val texture = state.cameraHolder.texture ?: return null
        val session = action.session

        val request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(texture.surface)
        }

        session.setRepeatingRequest(
            request.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    send(CameraAction.Stop)
                }
            },
            handler
        )

        return state.cameraHolder.copy(session = session)
    }

    private fun stopCamera(state: CameraState) {
        state.cameraHolder.session?.abortCaptures()
        state.cameraHolder.cameraDevice?.close()
    }
}

sealed class CameraAction : Action {
    class Open(val cameraDevice: CameraDevice?) : CameraAction()
    class SurfaceReady(val texture: OpenGLExternalTexture) : CameraAction()
    class SessionReady(val session: CameraCaptureSession) : CameraAction()
    object Stop : CameraAction()
    class Start(
        val manager: CameraManager,
        val surfaceProducer: SimpleProducer<OpenGLExternalTexture>
    ) : CameraAction()
}

sealed class CameraState(val cameraHolder: CameraHolder) : State {
    class WaitingStart : CameraState(CameraHolder())
    class WaitingOpen(cameraHolder: CameraHolder) : CameraState(cameraHolder)
    class WaitingSurface(cameraHolder: CameraHolder) : CameraState(cameraHolder)
    class WaitingSession(cameraHolder: CameraHolder) : CameraState(cameraHolder)
    class StartingPreview(cameraHolder: CameraHolder) : CameraState(cameraHolder)
}

data class CameraHolder(
    val manager: CameraManager? = null,
    val cameraId: String? = null,
    val cameraDevice: CameraDevice? = null,
    val session: CameraCaptureSession? = null,
    val texture: OpenGLExternalTexture? = null,
    val surfaceProducer: SimpleProducer<OpenGLExternalTexture>? = null,
    var size: Size? = null
)