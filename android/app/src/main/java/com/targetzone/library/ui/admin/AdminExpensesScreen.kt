package com.targetzone.library.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.data.model.SaveExpenseRequest
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.components.PrimaryButton
import com.targetzone.library.ui.theme.*
import java.time.LocalDate

private val MONTHS = listOf(
    "January","February","March","April","May","June",
    "July","August","September","October","November","December"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminExpensesScreen(vm: AdminViewModel) {
    val now      = LocalDate.now()
    var year     by remember { mutableIntStateOf(now.year) }
    var month    by remember { mutableIntStateOf(now.monthValue) }

    var tankerQty   by remember { mutableStateOf("0") }
    var tankerPrice by remember { mutableStateOf("") }
    var electricity by remember { mutableStateOf("") }
    var internet    by remember { mutableStateOf("") }
    var misc        by remember { mutableStateOf("") }

    val isLoading by vm.isLoading.collectAsState()
    val success   by vm.successMsg.collectAsState()
    val error     by vm.error.collectAsState()
    val expense   by vm.expense.collectAsState()

    // Populate fields when data loads
    LaunchedEffect(expense) {
        expense?.let { e ->
            tankerQty   = e.waterTankerQty.toString()
            tankerPrice = if (e.waterTankerPrice == 0.0) "" else e.waterTankerPrice.toBigDecimal().stripTrailingZeros().toPlainString()
            electricity = if (e.electricityBill == 0.0) "" else e.electricityBill.toBigDecimal().stripTrailingZeros().toPlainString()
            internet    = if (e.internetBill == 0.0) "" else e.internetBill.toBigDecimal().stripTrailingZeros().toPlainString()
            misc        = if (e.miscellaneous == 0.0) "" else e.miscellaneous.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    // Load on month/year change
    LaunchedEffect(year, month) { vm.loadExpenses(year, month) }

    LaunchedEffect(success) {
        if (success != null) { kotlinx.coroutines.delay(3000); vm.clearMessages() }
    }

    fun d(s: String) = s.toDoubleOrNull() ?: 0.0
    val qty          = tankerQty.toIntOrNull() ?: 0
    val waterTotal   = qty * d(tankerPrice)
    val grandTotal   = waterTotal + d(electricity) + d(internet) + d(misc)

    fun fmt(v: Double) = "₹%,.2f".format(v)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Monthly Expenses", style = MaterialTheme.typography.headlineMedium)
        Text("Track operating costs for the selected month", color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        // Feedback banners
        success?.let {
            Card(colors = CardDefaults.cardColors(containerColor = EmeraldFaint), modifier = Modifier.fillMaxWidth()) {
                Text("✅  $it", color = Emerald, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = RedFaint), modifier = Modifier.fillMaxWidth()) {
                Text("⚠️  $it", color = RedAlert, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        // Month / Year picker
        AppCard(Modifier.fillMaxWidth()) {
            Text("Period", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Month chips (scrollable subset: show 3 around current)
                Column(Modifier.weight(1f)) {
                    Text("Month", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Show prev, current, next month chips
                        val visibleMonths = ((month - 1)..(month + 1)).map { ((it - 1 + 12) % 12) + 1 }.distinct()
                        visibleMonths.forEach { m ->
                            FilterChip(
                                selected = month == m,
                                onClick = { month = m },
                                label = { Text(MONTHS[m - 1].take(3), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberFaint, selectedLabelColor = Amber)
                            )
                        }
                    }
                    // Full month dropdown via exposed dropdown
                    var monthExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = monthExpanded, onExpandedChange = { monthExpanded = it }) {
                        OutlinedTextField(
                            value = MONTHS[month - 1],
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 6.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                            MONTHS.forEachIndexed { idx, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { month = idx + 1; monthExpanded = false })
                            }
                        }
                    }
                }
                // Year
                Column(Modifier.weight(0.5f)) {
                    Text("Year", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    var yearExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = yearExpanded, onExpandedChange = { yearExpanded = it }) {
                        OutlinedTextField(
                            value = year.toString(),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = DividerColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                            listOf(now.year - 1, now.year, now.year + 1).forEach { y ->
                                DropdownMenuItem(text = { Text(y.toString()) }, onClick = { year = y; yearExpanded = false })
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Water tankers
        AppCard(Modifier.fillMaxWidth()) {
            Text("Water Tankers", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExpenseInput("Quantity", tankerQty, { tankerQty = it }, Modifier.weight(1f), isInteger = true)
                ExpenseInput("Price / Tanker (₹)", tankerPrice, { tankerPrice = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("Subtotal: ${fmt(waterTotal)}", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))

        // Other bills
        AppCard(Modifier.fillMaxWidth()) {
            Text("Other Bills", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            ExpenseInput("Electricity Bill (₹)", electricity, { electricity = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpenseInput("Internet Bill (₹)", internet, { internet = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ExpenseInput("Miscellaneous (₹)", misc, { misc = it }, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))

        // Total card
        Card(
            colors = CardDefaults.cardColors(containerColor = AmberFaint),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Expenses", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Text(fmt(grandTotal), fontWeight = FontWeight.Bold, color = Amber, fontSize = 22.sp)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Water Tankers" to waterTotal,
                    "Electricity"   to d(electricity),
                    "Internet"      to d(internet),
                    "Miscellaneous" to d(misc)
                ).forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = TextMuted, fontSize = 12.sp)
                        Text(fmt(value), color = TextPrimary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            text = if (isLoading) "Saving…" else "Save Expenses",
            enabled = !isLoading,
            onClick = {
                vm.saveExpenses(
                    SaveExpenseRequest(
                        year            = year,
                        month           = month,
                        waterTankerQty  = qty,
                        waterTankerPrice = d(tankerPrice),
                        electricityBill = d(electricity),
                        internetBill    = d(internet),
                        miscellaneous   = d(misc)
                    )
                ) {}
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExpenseInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isInteger: Boolean = false
) {
    Column(modifier) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Amber, unfocusedBorderColor = DividerColor,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                cursorColor = Amber
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )
    }
}
