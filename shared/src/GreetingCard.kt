package com.emilflach.groceries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.reload.DevelopmentEntryPoint

@Composable
fun GreetingCard(greeting: Greeting, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(greeting.message, style = MaterialTheme.typography.titleMedium)
            Text(greeting.body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview
@Composable
@DevelopmentEntryPoint
fun GreetingCardPreview() {
    MaterialTheme {
        GreetingCard(
            Greeting(
                message = "Hello from the IDE preview!",
                body = "",
            ),
        )
    }
}
