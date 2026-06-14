package com.targetzone.library.ui.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.targetzone.library.ui.components.AppCard
import com.targetzone.library.ui.theme.*

private data class Facility(val icon: String, val title: String, val desc: String)

private val FACILITIES = listOf(
    Facility("📶", "High-Speed Wi-Fi", "Dedicated fiber internet connection for uninterrupted study sessions"),
    Facility("🪑", "Ergonomic Seating", "110 premium seats across 4 rows with personal desk and reading lamp"),
    Facility("❄️", "Climate Control", "Year-round air conditioning for a comfortable study environment"),
    Facility("🔒", "Secure Lockers", "Personal storage lockers available for books and belongings"),
    Facility("☕", "Refreshment Zone", "Vending machines and a small café for snacks and beverages"),
    Facility("🚰", "Water Purifier", "RO-purified drinking water available throughout the library"),
    Facility("🖨️", "Print & Scan", "Printing, scanning, and photocopying at nominal charges"),
    Facility("📹", "CCTV Security", "24/7 CCTV surveillance for your safety and security"),
    Facility("🏠", "Study Rooms", "Private discussion rooms available on prior booking"),
    Facility("🌐", "Online Resources", "Access to digital library resources and e-books"),
)

@Composable
fun FacilitiesScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Facilities", style = MaterialTheme.typography.headlineMedium)
            Text("Everything you need to study efficiently", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Library Hours", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Morning Shift" to "6:00 AM – 2:00 PM",
                    "Evening Shift" to "2:00 PM – 10:00 PM",
                    "Full Day" to "6:00 AM – 10:00 PM",
                    "Weekly Off" to "None (Open 365 days)"
                ).forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = TextSub, fontSize = 13.sp)
                        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("AMENITIES", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
        }
        items(FACILITIES) { f ->
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row {
                    Text(f.icon, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(f.title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        Text(f.desc, color = TextSub, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
