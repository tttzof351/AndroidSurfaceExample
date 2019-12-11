package com.example.surfaces.helpers

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.example.surfaces.utils.LogUtils.debug
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException

class EncoderHelper(
    outputFile: File,
    val encoderWidth: Int,
    val encoderHeight: Int
) {
    val surface: Surface
    private val mediaCodec: MediaCodec
    private val bufferInfo = MediaCodec.BufferInfo()
    private val muxer: MediaMuxer

    private var trackIndex = -1
    private var muxerStarted = false

    init {
        val format = MediaFormat.createVideoFormat(VIDEO_FORMAT, encoderWidth, encoderHeight)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_PER_SECOND)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)

        try {
            mediaCodec = MediaCodec.createEncoderByType(VIDEO_FORMAT)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        surface = mediaCodec.createInputSurface()

        muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        mediaCodec.start()
        debug("encoder start")
    }

    fun drainEncoder(endOfStream: Boolean) {
        val timeoutUs = 10000
        debug("drainEncoder($endOfStream)")

        if (endOfStream) {
            debug("sending EOS to encoder")
            mediaCodec.signalEndOfInputStream()
        }

        var encoderOutputBuffers = mediaCodec.outputBuffers
        loop@ while (true) {
            val encoderStatus = mediaCodec.dequeueOutputBuffer(
                bufferInfo, timeoutUs.toLong()
            )

            when {
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) {
                        break@loop
                    } else {
                        debug("no output available, spinning to await EOS")
                    }
                }

                encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    encoderOutputBuffers = mediaCodec.outputBuffers
                }

                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        throw IllegalStateException("format changed twice")
                    }

                    val newFormat = mediaCodec.outputFormat
                    debug("encoder output format changed $newFormat")

                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }

                encoderStatus < 0 -> {
                    debug("unexpected result from dequeueOutput")
                }

                else -> {
                    val encodedData = encoderOutputBuffers[encoderStatus]
                        ?: throw IllegalStateException("encoded output buffer")

                    if (
                        (bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    ) {
                        debug("ignoring buffer_flag_codec")
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            throw IllegalStateException("muxer hasn't started")
                        }

                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(
                            bufferInfo.offset + bufferInfo.size
                        )

                        muxer.writeSampleData(
                            trackIndex, encodedData, bufferInfo
                        )

                        debug("send ${bufferInfo.size}")
                    }

                    mediaCodec.releaseOutputBuffer(encoderStatus, false)

                    if (
                        (bufferInfo.flags and
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    ) {
                        if (!endOfStream) {
                            debug("reached end of stream unexpectedly")
                        } else {
                            debug("end of stream reached")
                        }
                        break@loop
                    }
                }
            }
        }

    }

    fun release() {
        mediaCodec.stop()
        mediaCodec.release()

        muxer.stop()
        muxer.release()
    }

    private companion object {
        const val VIDEO_FORMAT = "video/avc"
        const val VIDEO_FRAME_PER_SECOND = 30
        const val VIDEO_I_FRAME_INTERVAL = 2
        const val VIDEO_BITRATE = 3000 * 1000
    }
}