package com.najishab.aether.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najishab.aether.R
import com.najishab.aether.core.Announcement
import java.util.Locale

/**
 * Dismissible in-app card for the current AnnouncementManager entry.
 * Deliberately a normal in-flow card (not a system notification) - see
 * AnnouncementManager's kdoc for why.
 */
@Composable
fun AnnouncementBanner(
    announcement: Announcement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFa = Locale.getDefault().language == "fa"
    val title = (if (isFa) announcement.titleFa else announcement.titleEn)
        .ifBlank { if (isFa) announcement.titleEn else announcement.titleFa }
    val text = (if (isFa) announcement.textFa else announcement.textEn)
        .ifBlank { if (isFa) announcement.textEn else announcement.textFa }
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.announcement_dismiss),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (announcement.url != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(announcement.url)))
                }) {
                    Text(stringResource(R.string.announcement_learn_more))
                }
            }
        }
    }
}
