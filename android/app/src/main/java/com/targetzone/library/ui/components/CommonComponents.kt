package com.targetzone.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.theme.*

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatCard(
    label: String,
    value: String,
    sub: String? = null,
    accent: Color = Amber,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.15f), Color.Transparent)))
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(50.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        colors = OutlinedButtonDefaults.outlinedButtonColors(contentColor = Amber),
        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Amber, Amber))),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(50.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSub) },
        enabled = enabled,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = DividerColor,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            disabledTextColor = TextSub,
            disabledBorderColor = DividerColor,
            cursorColor = Amber
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status.uppercase()) {
        "ACTIVE"    -> EmeraldFaint to Emerald
        "EXPIRED"   -> RedFaint to RedAlert
        "PENDING"   -> AmberFaint to Amber
        else        -> CardBg to TextSub
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(status, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSub)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (highlight) Amber else TextPrimary, fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal)
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Amber)
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚠️  $message", color = RedAlert, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlineButton("Retry", onRetry)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
