package com.uitesttools.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen() {
    var counter by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.Main.SCREEN)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Demo App",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(TestTags.Main.TITLE)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "$counter",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(TestTags.Main.COUNTER)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { counter-- },
                modifier = Modifier.testTag(TestTags.Main.DECREMENT_BUTTON)
            ) {
                Text("-", fontSize = 24.sp)
            }

            Button(
                onClick = { counter++ },
                modifier = Modifier.testTag(TestTags.Main.INCREMENT_BUTTON)
            ) {
                Text("+", fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { counter = 0 },
            modifier = Modifier.testTag(TestTags.Main.RESET_BUTTON)
        ) {
            Text("Reset")
        }
    }
}
