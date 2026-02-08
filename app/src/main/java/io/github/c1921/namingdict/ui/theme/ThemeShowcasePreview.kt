package io.github.c1921.namingdict.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.c1921.namingdict.ui.HanziFontFamily

@Composable
private fun ThemeShowcaseContent() {
    val spacing = AppTheme.spacing
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Text(
                text = "NamingDict",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "示例字：琬",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = HanziFontFamily,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Material 3 fallback/dynamic preview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Text(
                        text = "Card hierarchy sample",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Buttons and typography should stay legible in all themes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Primary")
                        }
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Secondary")
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Theme Light Fallback", showBackground = true)
@Composable
private fun ThemeShowcasePreviewLightFallback() {
    NamingDictTheme(darkTheme = false, dynamicColor = false) {
        ThemeShowcaseContent()
    }
}

@Preview(
    name = "Theme Dark Fallback",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun ThemeShowcasePreviewDarkFallback() {
    NamingDictTheme(darkTheme = true, dynamicColor = false) {
        ThemeShowcaseContent()
    }
}

@Preview(name = "Theme Dynamic Light", showBackground = true, apiLevel = 34)
@Composable
private fun ThemeShowcasePreviewDynamicLight() {
    NamingDictTheme(darkTheme = false, dynamicColor = true) {
        ThemeShowcaseContent()
    }
}
