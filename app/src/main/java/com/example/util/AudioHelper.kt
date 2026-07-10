package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AudioHelper {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordFile: File? = null

    fun startRecording(context: Context): Boolean {
        return try {
            stopRecording()
            val cacheDir = context.cacheDir
            currentRecordFile = File.createTempFile("audio_record", ".mp4", cacheDir)
            
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordFile!!.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val file = currentRecordFile
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            mediaRecorder = null
        }
    }

    fun playBase64Audio(context: Context, base64Audio: String, onComplete: () -> Unit) {
        try {
            stopPlayback()
            val tempFile = File.createTempFile("audio_play", ".mp4", context.cacheDir)
            val cleanAudio = if (base64Audio.contains(",")) base64Audio.substringAfter(",") else base64Audio
            val decodedBytes = Base64.decode(cleanAudio, Base64.DEFAULT)
            FileOutputStream(tempFile).use { fos ->
                fos.write(decodedBytes)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                    stopPlayback()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }
}
