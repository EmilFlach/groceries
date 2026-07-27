package com.emilflach.groceries

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.resources.Res
import com.emilflach.groceries.resources.app_title
import com.emilflach.groceries.resources.greeting_message
import com.emilflach.groceries.resources.kotlin_logo
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Preview
@Composable
@DevelopmentEntryPoint
fun App() {
    MaterialTheme {
        val greetingMessage = stringResource(Res.string.greeting_message)

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(Res.drawable.kotlin_logo),
                contentDescription = stringResource(Res.string.app_title),
                modifier = Modifier.size(64.dp),
            )
            Text(
                stringResource(Res.string.app_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            GreetingCard(Greeting(greetingMessage, "Running ${platformName()}"))
        }
    }
}
