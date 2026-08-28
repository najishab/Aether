package com.najishab.aether

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.najishab.aether.core.ChangelogParser
import com.najishab.aether.core.GithubRelease
import com.najishab.aether.core.GithubReleaseClient
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.ui.theme.AetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the current version's release notes, bundled offline via
 * assets/release-notes.md (see the copyReleaseNotes Gradle task), and
 * separately checks GitHub for a newer published release. If one exists,
 * its notes are shown above the bundled section with a link to open it.
 */
class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)

        val bundledRaw = runCatching {
            assets.open("release-notes.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            AetherTheme(themeMode = themeMode) {
                ChangelogScreen(
                    bundledRaw = bundledRaw,
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ChangelogScreen(bundledRaw: String, onBack: () -> Unit) {
    var remote by remember { mutableStateOf<GithubRelease?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isNotBlank()) {
            val release = withContext(Dispatchers.IO) {
                GithubReleaseClient.fetchLatestRelease(repo)
            }
            if (release != null && GithubReleaseClient.isNewer(release.tagName, BuildConfig.VERSION_NAME)) {
                remote = release
            }
        }
        checked = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.changelog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.changelog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                remote?.let { release -> NewerReleaseCard(release = release) }

                if (!checked && remote == null) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text(
                            text = stringResource(R.string.changelog_checking),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ChangelogSection(raw = bundledRaw)

                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewerReleaseCard(release: GithubRelease) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.changelog_new_version, release.tagName),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            release.body?.let { body ->
                ChangelogSection(raw = body, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            val url = release.htmlUrl ?: BuildConfig.RELEASES_URL
            if (url.isNotBlank()) {
                Button(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                }) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.changelog_view_release))
                }
            }
        }
    }
}

/** Renders one locale's section of a release-notes markdown blob. */
@Composable
private fun ChangelogSection(raw: String, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    val (en, fa) = remember(raw) { ChangelogParser.splitByLocale(raw) }
    val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.language ?: "en"
    val section = if (currentLocale == "fa" && fa != null) fa else en
    val lines = remember(section) { ChangelogParser.toLines(section) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in lines) {
            when (line) {
                is ChangelogParser.Line.Heading -> Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                )
                is ChangelogParser.Line.Bullet -> Row {
                    Text(text = "•  ", color = textColor)
                    Text(text = renderBold(line.text), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                is ChangelogParser.Line.Plain -> Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                )
            }
        }
    }
}

/** Converts **bold** markdown spans into an AnnotatedString with bold styling. */
@Composable
private fun renderBold(text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val start = text.indexOf("**", i)
            if (start == -1) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, start))
            val end = text.indexOf("**", start + 2)
            if (end == -1) {
                append(text.substring(start))
                break
            }
            withStyleBold { append(text.substring(start + 2, end)) }
            i = end + 2
        }
    }

private fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleBold(
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
    block()
    pop()
}
