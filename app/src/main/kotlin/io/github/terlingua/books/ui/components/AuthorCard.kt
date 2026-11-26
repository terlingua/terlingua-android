package io.github.terlingua.books.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.domain.Author
import io.github.terlingua.books.feedback.rememberFeedbackClick

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorCard(author: Author, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val links = buildList {
        author.socials.forEach { (key, value) -> socialUrl(key, value)?.let { add(key.replaceFirstChar(Char::uppercase) to it) } }
        author.email?.let { add("Email" to "mailto:$it") }
    }
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(author.avatarAsset, author.name, 56.dp)
                Text(author.name, style = MaterialTheme.typography.titleMedium)
            }
            author.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (links.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    links.forEach { (label, url) ->
                        AssistChip(
                            onClick = rememberFeedbackClick { runCatching { uriHandler.openUri(url) } },
                            label = { Text(label) },
                            modifier = Modifier.focusRing(),
                        )
                    }
                }
            }
        }
    }
}

fun socialUrl(key: String, value: String): String? {
    val v = value.trim()
    if (v.startsWith("https://") || v.startsWith("http://")) return v
    val handle = v.removePrefix("@")
    return when (key.lowercase()) {
        "github" -> "https://github.com/$handle"
        "gitlab" -> "https://gitlab.com/$handle"
        "codeberg" -> "https://codeberg.org/$handle"
        "x", "twitter" -> "https://x.com/$handle"
        "linkedin" -> "https://www.linkedin.com/in/$handle"
        "bluesky" -> "https://bsky.app/profile/$handle"
        "mastodon" -> handle.split('@').takeIf { it.size == 2 }?.let { (user, host) -> "https://$host/@$user" }
        "email" -> "mailto:$v"
        "website", "web", "homepage", "blog" -> "https://$v"
        else -> null
    }
}
