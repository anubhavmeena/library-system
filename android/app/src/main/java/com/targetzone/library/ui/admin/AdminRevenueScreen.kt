package com.targetzone.library.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.targetzone.library.data.model.DailyRevenue
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.SectionHeader
import com.targetzone.library.ui.components.StatCard
import com.targetzone.library.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AdminRevenueScreen(vm: AdminViewModel) {
    val report      by vm.revenueReport.collectAsState()
    val dailyPays   by vm.dailyPayments.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val error       by vm.error.collectAsState()

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = remember { Calendar.getInstance() }
    var fromDate by remember { mutableStateOf(sdf.format(cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.time)) }
    var toDate   by remember { mutableStateOf(sdf.format(Date())) }

    var drillDate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadRevenueReport(fromDate, toDate) }

    if (drillDate != null) {
        DrillDownDialog(
            date = drillDate!!,
            payments = dailyPays,
            isLoading = isLoading,
            onDismiss = { drillDate = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("Revenue & Reports", style = MaterialTheme.typography.headlineMedium)
            Text("Analyse income by date range", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        // Date range pickers
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Date Range", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromDate, onValueChange = { fromDate = it },
                        label = { Text("From", color = TextMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = toDate, onValueChange = { toDate = it },
                        label = { Text("To", color = TextMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Amber),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.loadRevenueReport(fromDate, toDate) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = NavyDeep),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isLoading) "Loading…" else "Load Report") }
            }
            Spacer(Modifier.height(16.dp))
        }

        error?.let {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                    Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        report?.let { r ->
            item {
                SectionHeader("Summary")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total Revenue", "₹${r.totalRevenue.toInt()}", accent = Emerald, modifier = Modifier.weight(1f))
                    StatCard("Transactions", r.totalTransactions.toString(), accent = Amber, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Full Day", r.fullDayCount.toString(), accent = BlueSoft, modifier = Modifier.weight(1f))
                    StatCard("Half Day", r.halfDayCount.toString(), accent = BlueSoft, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }

            if (r.dailyBreakdown.isNotEmpty()) {
                item { SectionHeader("Daily Breakdown") }
                items(r.dailyBreakdown) { day ->
                    DailyRevenueRow(day) {
                        drillDate = day.date
                        vm.loadDailyPayments(day.date)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DailyRevenueRow(day: DailyRevenue, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(day.date, fontWeight = FontWeight.Medium, color = TextPrimary, fontSize = 14.sp)
                Text("${day.transactionCount} transactions · Full ${day.fullDayCount} Half ${day.halfDayCount}", color = TextMuted, fontSize = 11.sp)
            }
            Text("₹${day.revenue.toInt()}", color = Emerald, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun DrillDownDialog(date: String, payments: List<com.targetzone.library.data.model.DailyPayment>, isLoading: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = NavyMid) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Payments on $date", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber, modifier = Modifier.size(32.dp))
                    }
                } else if (payments.isEmpty()) {
                    Text("No payments found", color = TextMuted, fontSize = 13.sp)
                } else {
                    payments.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(p.studentName ?: "—", color = TextPrimary, fontSize = 13.sp)
                                Text(p.planName ?: "—", color = TextMuted, fontSize = 11.sp)
                            }
                            Text("₹${p.amount.toInt()}", color = Emerald, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = DividerColor)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close", color = Amber) }
            }
        }
    }
}
