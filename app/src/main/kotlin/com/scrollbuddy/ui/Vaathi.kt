package com.scrollbuddy.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.scrollbuddy.R

/**
 * The character's animations, cut by art/make_clips.py out of one generated video and packed
 * into animated WebP files. Every clip is [FRAME_HEIGHT] pixels tall and centred on the
 * character, so clips of different widths line up when each is shown at the same height and
 * centred on the same spot.
 *
 * The video has two shots. A full shot shows him head to foot. A close shot shows him from the
 * thighs up, [CLOSE_SHOT_SCALE] times larger, cut off by the bottom edge of the frame, so it
 * belongs at the bottom of whatever it is shown in.
 */
enum class VaathiClip(@param:RawRes val resource: Int, private val frameWidth: Int, val closeShot: Boolean = false) {
    /** Full shot: takes a hand out of his pocket, waves and puts it back; plays once. */
    Wave(R.raw.vaathi_wave, frameWidth = 448),

    /** Close shot: raises his hand into a stop sign; plays once, into [Hold]. */
    Stop(R.raw.vaathi_stop, frameWidth = 496, closeShot = true),

    /** Close shot: holds the stop sign up; loops. */
    Hold(R.raw.vaathi_hold, frameWidth = 496, closeShot = true),

    /** Full shot: lays a flat hand on his shoulder and sweeps it out to his side; plays once. */
    Sweep(R.raw.vaathi_sweep, frameWidth = 704);

    /** Width over height of the clip's frames. */
    val aspect: Float get() = frameWidth / FRAME_HEIGHT
}

private const val FRAME_HEIGHT = 720f

/** How many times larger the character is in a close shot than in a full one. */
const val CLOSE_SHOT_SCALE = 1.52f

/**
 * How far down a full shot's frame, as a share of its height, the point is that a close shot
 * leaves where it was: a full shot enlarged [CLOSE_SHOT_SCALE] times about that point, half way
 * across, has his head where the close shot has it.
 */
const val CLOSE_SHOT_PIVOT_Y = 0.051f

/**
 * Plays [clip], over and over if [loop] is set, otherwise once and then calls [onFinished] with
 * the last frame left showing. Give it a height or a width; the other follows from the clip.
 *
 * The platform's AnimatedImageDrawable does the playing: it decodes frames as they are needed,
 * so a clip costs about two frames of memory however long it is.
 */
@Composable
fun Vaathi(
    clip: VaathiClip,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    onFinished: () -> Unit = {},
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    AndroidView(
        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            val playing = clip to loop
            if (view.tag == playing) return@AndroidView
            view.tag = playing
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(view.resources, clip.resource))
            // The new clip's first frame replaces the old clip's last in the same pass, so
            // nothing blank is ever drawn between them
            view.setImageDrawable(drawable)
            if (drawable is AnimatedImageDrawable) {
                drawable.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
                drawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(ended: Drawable) = currentOnFinished()
                })
                drawable.start()
            }
        },
        modifier = modifier.aspectRatio(clip.aspect),
    )
}
