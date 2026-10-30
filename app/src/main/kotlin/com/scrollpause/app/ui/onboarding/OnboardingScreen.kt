package com.scrollpause.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrollpause.app.R
import com.scrollpause.app.ui.theme.MintPrimary
import com.scrollpause.app.ui.theme.NavyBackground
import com.scrollpause.app.ui.theme.NavySurface
import com.scrollpause.app.ui.theme.OutlineColor

private data class OnboardingPage(
    val title: String,
    val body: String,
    val illustration: @Composable () -> Unit,
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val pages = rememberOnboardingPages()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 24.dp),
    ) {
        // Skip button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (currentPage < pages.size - 1) {
                TextButton(onClick = {
                    viewModel.completeOnboarding()
                    onComplete()
                }) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Page content with slide animation
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            modifier = Modifier.weight(1f),
            label = "onboarding_page",
        ) { page ->
            OnboardingPageContent(page = pages[page])
        }

        // Permission buttons on page 2 (index 2)
        if (currentPage == 2) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_accessibility_button))
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_overlay_button))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == currentPage) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (index == currentPage) MintPrimary else OutlineColor)
                        .semantics {
                            contentDescription = "Page ${index + 1} of ${pages.size}"
                        },
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (currentPage < pages.size - 1) {
                Button(
                    onClick = { currentPage++ },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_next),
                        color = NavyBackground,
                    )
                }
            } else {
                Button(
                    onClick = {
                        viewModel.completeOnboarding()
                        onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_get_started),
                        color = NavyBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.size(160.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                page.illustration()
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun rememberOnboardingPages(): List<OnboardingPage> {
    val context = LocalContext.current
    return remember {
        listOf(
            OnboardingPage(
                title = context.getString(R.string.onboarding_page1_title),
                body = context.getString(R.string.onboarding_page1_body),
                illustration = { SeedlingIllustration() },
            ),
            OnboardingPage(
                title = context.getString(R.string.onboarding_page2_title),
                body = context.getString(R.string.onboarding_page2_body),
                illustration = { PrivacyIllustration() },
            ),
            OnboardingPage(
                title = context.getString(R.string.onboarding_page3_title),
                body = context.getString(R.string.onboarding_page3_body),
                illustration = { PermissionsIllustration() },
            ),
        )
    }
}

// ── Illustrations ─────────────────────────────────────────────────────────────

@Composable
private fun SeedlingIllustration() {
    Text(text = "🌱", style = MaterialTheme.typography.displayLarge)
}

@Composable
private fun PrivacyIllustration() {
    Text(text = "🔒", style = MaterialTheme.typography.displayLarge)
}

@Composable
private fun PermissionsIllustration() {
    Text(text = "✅", style = MaterialTheme.typography.displayLarge)
}
