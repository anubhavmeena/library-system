package com.targetzone.library.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.Plan
import com.targetzone.library.ui.haptics.rememberLibraryHaptics
import com.targetzone.library.ui.theme.*

// Shared semantic tone for PrimaryButton/OutlineButton — capped at exactly these
// 4 values on purpose, so the app never grows a 3rd/4th button component for
// "just one more color". Danger/Success cover every destructive or
// success-confirming action site found across the app; Neutral covers
// secondary/utility actions (e.g. an "Actions ▾" menu trigger).
enum class ButtonTone { Amber, Neutral, Danger, Success }

private fun ButtonTone.solidContainer(): Color = when (this) {
    ButtonTone.Amber   -> Amber
    ButtonTone.Neutral -> NavyLight
    ButtonTone.Danger  -> RedAlert
    ButtonTone.Success -> Emerald
}

private fun ButtonTone.solidContent(): Color = when (this) {
    ButtonTone.Amber   -> NavyDeep
    ButtonTone.Neutral -> TextPrimary
    ButtonTone.Danger  -> Color.White
    ButtonTone.Success -> NavyDeep
}

private fun ButtonTone.outline(): Color = when (this) {
    ButtonTone.Amber   -> Amber
    ButtonTone.Neutral -> TextSub
    ButtonTone.Danger  -> RedAlert
    ButtonTone.Success -> Emerald
}

/**
 * Flat layout chrome by default (matches the app's original look exactly).
 * Supplying [onClick] opts a card into being an interactive surface: ripple,
 * a light press-scale, and a haptic tick — the 59+ pre-existing non-interactive
 * usages are unaffected since onClick defaults to null.
 *
 * No elevation shadow on either branch: `Modifier.shadow(..., clip = false)`
 * renders as a faint but visible rectangular tint behind the rounded card on
 * this rendering pipeline (confirmed on device, worse on StatCard's gradient
 * background but present here too) instead of a soft drop shadow. The border
 * already reads as a distinct, elevated surface without it.
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    if (onClick == null) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(CardBg)
                .border(1.dp, DividerColor, shape)
                .padding(16.dp),
            content = content
        )
    } else {
        val haptics = rememberLibraryHaptics()
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "cardPressScale")
        Column(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(CardBg)
                .border(1.dp, DividerColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { haptics.tick(); onClick() }
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    sub: String? = null,
    accent: Color = Amber,
    modifier: Modifier = Modifier
) {
    // No elevation shadow here — at this card size/shape it rendered as a
    // hard-edged rectangular glow instead of a soft drop shadow (confirmed on
    // device, not just a screenshot artifact), regardless of shadow color.
    // The gradient + border already read as a distinct, elevated-looking
    // surface without it.
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent)))
            .border(1.dp, accent.copy(alpha = 0.25f), shape)
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Amber,
    icon: ImageVector? = null
) {
    val haptics = rememberLibraryHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "primaryBtnScale")
    Button(
        onClick = {
            if (tone == ButtonTone.Danger) haptics.reject() else haptics.tick()
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = tone.solidContainer(), contentColor = tone.solidContent()),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(50.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.Amber,
    icon: ImageVector? = null,
    height: androidx.compose.ui.unit.Dp = 50.dp
) {
    val haptics = rememberLibraryHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "outlineBtnScale")
    OutlinedButton(
        onClick = {
            if (tone == ButtonTone.Danger) haptics.reject() else haptics.tick()
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tone.outline()),
        border = ButtonDefaults.outlinedButtonBorder(enabled).copy(brush = Brush.linearGradient(listOf(tone.outline(), tone.outline()))),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
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
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSub) },
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = DividerColor,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            disabledTextColor = TextSub,
            disabledBorderColor = DividerColor,
            cursorColor = Amber
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = when (status.uppercase()) {
        "ACTIVE"    -> EmeraldFaint to Emerald
        "EXPIRED"       -> RedFaint to RedAlert
        // GRACE now reads as urgent (red), matching the web dashboard/membership
        // pages — a student owing dues to keep their seat shouldn't look "fine".
        "GRACE"         -> RedFaint to RedAlert
        // displayStatus (not the raw membershipStatus above) uses this key for
        // an overdue-grace student — see resolve_display_status in the backend.
        "GRACE_OVERDUE" -> RedFaint to RedAlert
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

// Full-width message/status banner — distinct in name and shape from StatusChip
// (a small pill badge) to avoid confusing the two. Replaces the ad-hoc
// `Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint/RedFaint/...))`
// pattern that was duplicated inline across ~16 screens.
enum class BannerTone { Success, Error, Warning, Info }

@Composable
fun MessageBanner(message: String, tone: BannerTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        BannerTone.Success -> EmeraldFaint to Emerald
        BannerTone.Error   -> RedFaint to RedAlert
        BannerTone.Warning -> AmberFaint to Amber
        BannerTone.Info    -> BlueFaint to BlueSoft
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = fg, fontSize = 13.sp)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(color = Amber)
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚠️  $message", color = RedAlert, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlineButton(text = "Retry", onClick = onRetry)
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

// Selectable plan card — shared by the student booking flow and the admin
// create-membership flow, since both let someone pick a plan from a list.
@Composable
fun PlanCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberLibraryHaptics()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AmberFaint else CardBg)
            .border(1.dp, if (selected) Amber else DividerColor, RoundedCornerShape(16.dp))
            .clickable { haptics.tick(); onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium)
                    Text(plan.description, color = TextSub, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${plan.price.toInt()}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Amber)
                    Text("/month", color = TextMuted, fontSize = 11.sp)
                }
            }
            if (plan.planType == "FULL_DAY") {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(AmberFaint).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("Most Popular", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
