package com.example.tvreceiver

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var fullscreenButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var castServer: CastServer
    private lateinit var servicePublisher: ServicePublisher

    private var isFullscreen = false

    private val shortSeekMs = 10_000L
    private val longSeekStepMs = 2_000L
    private val longSeekIntervalMs = 200L

    private val uiHandler = Handler(Looper.getMainLooper())
    private var longSeekForward = false
    private var longSeekBackward = false

    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgressText()
            uiHandler.postDelayed(this, 500)
        }
    }

    private val longSeekTicker = object : Runnable {
        override fun run() {
            val hasMedia = player.currentMediaItem != null
            if (!hasMedia) {
                stopLongSeek()
                return
            }
            when {
                longSeekForward -> {
                    seekByInternal(longSeekStepMs)
                    updateStatus("连续快进中")
                    uiHandler.postDelayed(this, longSeekIntervalMs)
                }

                longSeekBackward -> {
                    seekByInternal(-longSeekStepMs)
                    updateStatus("连续快退中")
                    uiHandler.postDelayed(this, longSeekIntervalMs)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootLayout = findViewById(R.id.rootLayout)
        statusText = findViewById(R.id.statusText)
        progressText = findViewById(R.id.progressText)
        fullscreenButton = findViewById(R.id.fullscreenButton)
        playerView = findViewById(R.id.playerView)

        applyBlackSystemBars()

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.setShutterBackgroundColor(Color.BLACK)
        playerView.setBackgroundColor(Color.BLACK)
        fullscreenButton.setOnClickListener { toggleFullscreen() }

        servicePublisher = ServicePublisher(this)
        castServer = CastServer { url ->
            runOnUiThread {
                statusText.text = "收到视频链接: $url"
                playUrl(url)
            }
        }

        castServer.start()
        servicePublisher.register(CastServer.PORT)
        statusText.text = "服务已启动，等待手机发送..."
        updateProgressText()
        uiHandler.post(progressTicker)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        togglePlayPause()
                        return true
                    }

                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        pausePlayback()
                        return true
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        resumePlayback()
                        return true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        handleSeekKeyDown(isForward = true, repeatCount = event.repeatCount)
                        return true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        handleSeekKeyDown(isForward = false, repeatCount = event.repeatCount)
                        return true
                    }

                    KeyEvent.KEYCODE_MENU -> {
                        showSpeedMenu()
                        return true
                    }

                    KeyEvent.KEYCODE_BACK -> {
                        handleBack()
                        return true
                    }
                }
            }

            KeyEvent.ACTION_UP -> {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        stopLongSeek()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applyBlackSystemBars() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isFullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            statusText.visibility = View.GONE
            progressText.visibility = View.GONE
            fullscreenButton.visibility = View.GONE
            rootLayout.setPadding(0, 0, 0, 0)
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            statusText.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            fullscreenButton.visibility = View.VISIBLE
            val dp24 = (24 * resources.displayMetrics.density).toInt()
            rootLayout.setPadding(dp24, dp24, dp24, dp24)
        }
        applyBlackSystemBars()
    }

    private fun handleSeekKeyDown(isForward: Boolean, repeatCount: Int) {
        if (player.currentMediaItem == null) return
        if (repeatCount == 0) {
            seekBy(if (isForward) shortSeekMs else -shortSeekMs)
            return
        }
        if (repeatCount >= 2) {
            startLongSeek(isForward)
        }
    }

    private fun startLongSeek(isForward: Boolean) {
        if (isForward && longSeekForward) return
        if (!isForward && longSeekBackward) return

        longSeekForward = isForward
        longSeekBackward = !isForward
        uiHandler.removeCallbacks(longSeekTicker)
        uiHandler.post(longSeekTicker)
    }

    private fun stopLongSeek() {
        longSeekForward = false
        longSeekBackward = false
        uiHandler.removeCallbacks(longSeekTicker)
    }

    private fun playUrl(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        updateStatus("开始播放")
        updateProgressText()
    }

    private fun togglePlayPause() {
        if (player.currentMediaItem == null) return
        if (player.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun pausePlayback() {
        if (player.currentMediaItem == null) return
        player.pause()
        updateStatus("已暂停")
        Toast.makeText(this, "已暂停", Toast.LENGTH_SHORT).show()
    }

    private fun resumePlayback() {
        if (player.currentMediaItem == null) return
        player.playWhenReady = true
        updateStatus("播放中")
        Toast.makeText(this, "继续播放", Toast.LENGTH_SHORT).show()
    }

    private fun seekBy(deltaMs: Long) {
        if (player.currentMediaItem == null) return
        seekByInternal(deltaMs)
        val tip = if (deltaMs > 0) "快进 10 秒" else "快退 10 秒"
        updateStatus(tip)
        Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
    }

    private fun seekByInternal(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        updateProgressText()
    }

    private fun showSpeedMenu() {
        if (player.currentMediaItem == null) {
            Toast.makeText(this, "当前没有可播放内容", Toast.LENGTH_SHORT).show()
            return
        }
        val speeds = floatArrayOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
        val labels = arrayOf("0.5x", "1.0x", "1.25x", "1.5x", "2.0x")
        val current = player.playbackParameters.speed
        val selected = speeds.indices.minByOrNull { i -> kotlin.math.abs(speeds[i] - current) } ?: 1

        AlertDialog.Builder(this)
            .setTitle("播放设置")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                player.playbackParameters = PlaybackParameters(speeds[which])
                updateStatus("倍速 ${labels[which]}")
                updateProgressText()
                Toast.makeText(this, "已切换到 ${labels[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNeutralButton(if (isFullscreen) "退出全屏" else "进入全屏") { _, _ ->
                toggleFullscreen()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleBack() {
        if (isFullscreen) {
            toggleFullscreen()
            return
        }

        if (player.currentMediaItem != null) {
            player.stop()
            player.clearMediaItems()
            stopLongSeek()
            statusText.text = "已停止播放，等待手机发送..."
            updateProgressText()
            Toast.makeText(this, "已返回到等待状态", Toast.LENGTH_SHORT).show()
        } else {
            finish()
        }
    }

    private fun updateStatus(prefix: String) {
        val speed = String.format(Locale.US, "%.2fx", player.playbackParameters.speed)
        statusText.text = "$prefix | 倍速 $speed"
    }

    private fun updateProgressText() {
        val current = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val speed = String.format(Locale.US, "%.2fx", player.playbackParameters.speed)
        progressText.text = "${formatTime(current)} / ${formatTime(duration)} | $speed"
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val hour = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (hour > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hour, min, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", min, sec)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLongSeek()
        uiHandler.removeCallbacks(progressTicker)
        servicePublisher.unregister()
        castServer.stop()
        player.release()
    }
}
