package io.max.paperplay.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.max.paperplay.ui.components.AppIcon

/** "Made by Max with ❤ and ☕" for the end of a page. The heart and the cup are icons in the theme's accent colour, not text emoji. */
@Composable
fun MadeBy(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.bodyMedium
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text("Made by Max with ", style = style, color = muted)
        AppIcon(Icons.Rounded.Favorite, "love", Modifier.size(16.dp), tint = accent)
        Text(" and ", style = style, color = muted)
        AppIcon(Icons.Rounded.LocalCafe, "coffee", Modifier.size(16.dp), tint = accent)
    }
}
