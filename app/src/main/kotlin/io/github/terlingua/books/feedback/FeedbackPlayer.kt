package io.github.terlingua.books.feedback

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.terlingua.books.R
import java.util.concurrent.ConcurrentHashMap

enum class FeedbackEvent { Tap, Toggle, Correct, Incorrect }

/**
 * Plays tiny bundled sonification cues. Sounds are only played while the app is in the foreground
 * (Android 17 restricts background audio) and never in silent, vibrate or Do Not Disturb modes.
 */
class FeedbackPlayer(context: Context) : DefaultLifecycleObserver {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .apply { setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id } }

    private val sounds = mapOf(
        FeedbackEvent.Tap to pool.load(context, R.raw.feedback_tap, 1),
        FeedbackEvent.Toggle to pool.load(context, R.raw.feedback_toggle, 1),
        FeedbackEvent.Correct to pool.load(context, R.raw.feedback_correct, 1),
        FeedbackEvent.Incorrect to pool.load(context, R.raw.feedback_incorrect, 1),
    )

    @Volatile private var foreground = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        pool.autoResume()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        pool.autoPause()
    }

    fun play(event: FeedbackEvent) {
        if (!foreground || !audibleNow()) return
        val id = sounds[event]?.takeIf { it in loaded } ?: return
        val volume = if (event == FeedbackEvent.Tap || event == FeedbackEvent.Toggle) 0.35f else 0.6f
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    private fun audibleNow(): Boolean {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val filter = notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        return filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}
