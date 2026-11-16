package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Shared lift from the mode/lorebook lists; default Compose spring also settles the release. */
@Composable
internal fun Modifier.promptReorderLift(dragging: Boolean): Modifier {
    val scale by animateFloatAsState(if (dragging) 1.05f else 1f, label = "prompt_reorder_lift")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
