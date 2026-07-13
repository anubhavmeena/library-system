package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.Coupon
import com.targetzone.library.ui.components.*
import com.targetzone.library.ui.theme.*

@Composable
fun AdminCouponsScreen(vm: AdminViewModel) {
    val coupons     by vm.coupons.collectAsState()
    val appSettings by vm.appSettings.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val error       by vm.error.collectAsState()
    val successMsg  by vm.successMsg.collectAsState()

    var code            by remember { mutableStateOf("") }
    var discountPercent by remember { mutableStateOf("") }
    var deleteTarget    by remember { mutableStateOf<Coupon?>(null) }

    LaunchedEffect(Unit) { vm.loadCoupons(); vm.loadAppSettings() }
    LaunchedEffect(successMsg) {
        if (successMsg != null) { kotlinx.coroutines.delay(2000); vm.clearMessages() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Coupons", style = MaterialTheme.typography.headlineMedium)
        Text("Create and manage promotional discount codes", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        successMsg?.let { MessageBanner("✅  $it", BannerTone.Success); Spacer(Modifier.height(8.dp)) }
        error?.let { MessageBanner("⚠️  $it", BannerTone.Error); Spacer(Modifier.height(8.dp)) }

        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enable Discount Coupons", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        "Turning this off immediately hides all coupons from students and disables discounts.",
                        color = TextSub, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = appSettings?.couponsEnabled ?: true,
                    onCheckedChange = { vm.setCouponsEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberFaint)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Create Coupon")
        AppCard(Modifier.fillMaxWidth()) {
            AppTextField(value = code, onValueChange = { code = it.uppercase() }, label = "Coupon Code (optional)")
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = discountPercent,
                onValueChange = { discountPercent = it.filter(Char::isDigit) },
                label = "Discount %"
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Create Coupon",
                enabled = discountPercent.toIntOrNull()?.let { it in 1..100 } == true,
                onClick = {
                    vm.createCoupon(code.trim().takeIf { it.isNotBlank() }, discountPercent.toInt())
                    code = ""; discountPercent = ""
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("All Coupons")
        if (isLoading && coupons.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else if (coupons.isEmpty()) {
            Text("No coupons created yet.", color = TextSub, fontSize = 13.sp)
        } else {
            coupons.forEach { coupon ->
                AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(coupon.code, fontWeight = FontWeight.Bold, color = Amber)
                            Text("${coupon.discountPercent}% off", color = TextSub, fontSize = 12.sp)
                        }
                        Switch(
                            checked = coupon.isActive,
                            onCheckedChange = { vm.setCouponActive(coupon, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberFaint)
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { deleteTarget = coupon }) { Text("Delete", color = RedAlert) }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete coupon ${target.code}?",
            subtitle = "This cannot be undone.",
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.deleteCoupon(target); deleteTarget = null },
            confirmLabel = "Delete",
            confirmTone = ButtonTone.Danger
        )
    }
}
