package com.alterlingua.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.AlterLinguaMark
import kotlinx.coroutines.delay

/**
 * First launch, before any question: the app's promise (Stitch A1's headline, "You write in your language. They
 * receive theirs."), shown once so a new user understands what AlterLingua does before being asked to choose
 * anything. Followed by [AppLanguageScreen]. Design principles (CLAUDE.md 36): calm and premium, one purposeful
 * animation rather than several decorative ones.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag("screen_welcome"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AlterLinguaMark(Modifier.size(88.dp).testTag("welcome_mark"))
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.welcome_headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            GreetingCarousel()
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.welcome_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("welcome_get_started"),
            ) {
                Text(stringResource(R.string.welcome_get_started), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_no_account_note),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The languages AlterLingua translates into, each shown for a moment in its own script: a small, honest
 * demonstration of the product rather than a decorative animation. */
private val greetings: List<Pair<Language, String>> = listOf(
    Languages.French to "Bonjour",
    Languages.Spanish to "Hola",
    Languages.German to "Hallo",
    Languages.Italian to "Ciao",
    Languages.Dutch to "Hallo",
    Languages.Chinese to "你好",
    Languages.Japanese to "こんにちは",
)

@Composable
private fun GreetingCarousel(modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200)
            index = (index + 1) % greetings.size
        }
    }
    AlterLinguaCard(modifier = modifier.testTag("welcome_greeting_carousel")) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.welcome_greeting_caption),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
                },
                label = "welcome_greeting",
            ) { i ->
                val (language, greeting) = greetings[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(language.nativeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
