package com.xnotes.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.xnotes.R

/**
 * The in-app Saya mark, sourced from the same transparent artwork used by the adaptive launcher
 * icon. Keeping one asset prevents the home/splash wordmark from drifting away from the app icon.
 *
 * [progress] reveals the mark from bottom to top for the existing draw-on entrance animation.
 */
@Composable
fun NLogo(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    progress: Float = 1f,
) {
    Image(
        painter = painterResource(R.drawable.saya_logo_transparent),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.drawWithContent {
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                clipRect(top = size.height * (1f - p)) {
                    drawContent()
                }
            }
        },
    )
}
