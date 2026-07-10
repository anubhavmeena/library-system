package com.targetzone.library.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.NavyMid
import com.targetzone.library.ui.theme.TextPrimary
import com.targetzone.library.ui.theme.TextSub

// Shared dialog chrome — replaces the app-wide, ~15-times-duplicated
// `Dialog(){ Surface(shape=16dp, color=NavyMid){ Column(padding=20dp){...} } }`
// pattern with one component that adds a fade+scale entrance (previously zero
// dialog animation existed anywhere in the app). Exit is the platform's default
// dialog dismissal — an animated exit would require delaying the caller's
// dialog-open boolean flip until after the animation finishes, which is a real
// timing hazard for dialogs that gate mutating actions (delete, clear dues,
// release seat); entrance-only animation gets most of the "feels modern" win
// with none of that risk.
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f)
        ) {
            // No explicit widthIn cap: some dialog bodies (the seat-picker grid,
            // in particular) need more width than a fixed cap like 420dp allows
            // and would get silently clipped — the platform's own default dialog
            // max-width behavior (same as the pre-refactor Dialog+Surface here)
            // already keeps plain text dialogs from stretching too wide.
            Surface(shape = RoundedCornerShape(20.dp), color = NavyMid, shadowElevation = 12.dp) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, color = TextSub, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

// Confirm/Cancel variant of FormDialog — body is optional (a plain
// title+confirm/cancel dialog, e.g. delete confirmation, needs no body).
@Composable
fun ConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    confirmEnabled: Boolean = true,
    confirmTone: ButtonTone = ButtonTone.Amber,
    subtitle: String? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null
) {
    FormDialog(title = title, onDismiss = onDismiss, subtitle = subtitle) {
        body?.invoke(this)
        if (body != null) Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = TextSub) }
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                tone = confirmTone,
                modifier = Modifier.height(42.dp)
            )
        }
    }
}
