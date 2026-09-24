package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var isError by remember { mutableStateOf(false) }

    fun submit() {
        val parsed = pageInput.toIntOrNull()
        if (parsed != null && parsed in 1..totalPages) {
            onJumpToPage(parsed)
            onDismiss()
        } else {
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Jump to Page",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter a page number between 1 and $totalPages:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        pageInput = filtered
                        val intVal = filtered.toIntOrNull()
                        if (intVal != null && intVal in 1..totalPages) {
                            sliderValue = intVal.toFloat()
                            isError = false
                        } else {
                            isError = filtered.isNotEmpty()
                        }
                    },
                    label = { Text("Page Number") },
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("Please enter a valid page between 1 and $totalPages")
                        } else {
                            Text("Currently at page $currentPage of $totalPages")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jump_page_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive slider
                Text(
                    text = "Or drag to scrub:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        pageInput = it.toInt().toString()
                        isError = false
                    },
                    valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                    steps = (totalPages - 2).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                modifier = Modifier.testTag("jump_page_confirm")
            ) {
                Text("Go to Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
