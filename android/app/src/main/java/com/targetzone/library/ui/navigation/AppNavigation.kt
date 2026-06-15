package com.targetzone.library.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.targetzone.library.data.TokenManager
import com.targetzone.library.data.model.User
import com.targetzone.library.data.repository.AuthRepository
import com.targetzone.library.ui.SplashScreen
import com.targetzone.library.ui.admin.*
import com.targetzone.library.ui.auth.*
import com.targetzone.library.ui.student.*
import com.targetzone.library.ui.theme.*
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val icon: ImageVector, val label: String)

private val studentNavItems = listOf(
    NavItem("dashboard",  Icons.Default.Home,          "Home"),
    NavItem("membership", Icons.Default.CardMembership, "Membership"),
    NavItem("booking",    Icons.Default.EventSeat,      "Book"),
    NavItem("profile",    Icons.Default.Person,         "Profile"),
)

private val adminNavItems = listOf(
    NavItem("admin/dashboard",   Icons.Default.Dashboard,    "Dashboard"),
    NavItem("admin/students",    Icons.Default.Group,        "Students"),
    NavItem("admin/seats",       Icons.Default.AirlineSeatReclineNormal, "Seats"),
    NavItem("admin/reminders",   Icons.Default.Notifications,"Alerts"),
    NavItem("admin/feedback",    Icons.Default.Feedback,     "Feedback"),
)

@Composable
fun AppNavigation(tokenManager: TokenManager) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(tokenManager) }

    // Seed initial user from DataStore
    val savedUser by tokenManager.getUser().collectAsState(initial = null)
    var currentUser by remember { mutableStateOf<User?>(null) }
    LaunchedEffect(savedUser) { currentUser = savedUser }

    val authVm = remember { AuthViewModel(authRepo) }
    val studentVm = remember { StudentViewModel(tokenManager = tokenManager) }
    val adminVm = remember { AdminViewModel() }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: ""

    val isStudentRoute = studentNavItems.any { currentRoute.startsWith(it.route) } || currentRoute in listOf("booking", "membership", "profile", "facilities", "feedback", "payment-success")
    val isAdminRoute   = adminNavItems.any { currentRoute.startsWith(it.route) } || currentRoute.startsWith("admin/")

    Scaffold(
        containerColor = NavyDeep,
        bottomBar = {
            when {
                isStudentRoute -> StudentBottomBar(currentRoute, navController)
                isAdminRoute   -> AdminBottomBar(currentRoute, navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(padding)
        ) {
            composable("splash") {
                SplashScreen {
                    val dest = when {
                        currentUser?.role == "ADMIN"   -> "admin/dashboard"
                        currentUser?.role == "STUDENT" -> "dashboard"
                        else -> "login"
                    }
                    navController.navigate(dest) { popUpTo("splash") { inclusive = true } }
                }
            }

            // Auth
            composable("login") {
                LoginScreen(
                    vm = authVm,
                    onNavigateToRegister = { token -> navController.navigate("register/$token") },
                    onLoginSuccess = {
                        currentUser = authVm.state.value.user
                        navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                    },
                    onAdminLogin = { navController.navigate("admin-login") }
                )
            }
            composable("register/{token}") { back ->
                RegisterScreen(
                    vm = authVm,
                    sessionToken = back.arguments?.getString("token") ?: "",
                    onSuccess = {
                        currentUser = authVm.state.value.user
                        navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                    }
                )
            }
            composable("admin-login") {
                AdminLoginScreen(
                    vm = authVm,
                    onSuccess = {
                        currentUser = authVm.state.value.user
                        navController.navigate("admin/dashboard") { popUpTo("login") { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            val logout: () -> Unit = {
                scope.launch { authRepo.logout(); currentUser = null; navController.navigate("login") { popUpTo(0) { inclusive = true } } }
            }

            // Student routes
            composable("dashboard")       { DashboardScreen(studentVm, currentUser) { route -> navController.navigate(route) } }
            composable("booking")         { BookingScreen(studentVm) { navController.navigate("payment-success") { popUpTo("booking") { inclusive = true } } } }
            composable("membership")      { MembershipScreen(studentVm) { navController.navigate("booking") } }
            composable("profile")         { ProfileScreen(studentVm, onLogout = logout) }
            composable("facilities")      { FacilitiesScreen() }
            composable("feedback")        { FeedbackScreen(studentVm) }
            composable("payment-success") { PaymentSuccessScreen(studentVm) { navController.navigate("dashboard") { popUpTo("payment-success") { inclusive = true } } } }

            // Admin routes
            composable("admin/dashboard") {
                AdminScaffold("Admin Dashboard", onLogout = logout) {
                    AdminDashboardScreen(adminVm) { route -> navController.navigate(if (route.startsWith("admin/")) route else "admin/$route") }
                }
            }
            composable("admin/students")  {
                AdminScaffold("Students", onLogout = logout) {
                    AdminStudentsScreen(adminVm) { studentId -> navController.navigate("admin/students/$studentId") }
                }
            }
            composable("admin/students/{studentId}") { back ->
                val studentId = back.arguments?.getString("studentId") ?: ""
                AdminScaffold("Student Details", onBack = { navController.popBackStack() }) {
                    AdminStudentDetailScreen(adminVm, studentId) { navController.popBackStack() }
                }
            }
            composable("admin/seats")     {
                AdminScaffold("Seat Map", onLogout = logout) { AdminSeatsScreen(adminVm) }
            }
            composable("admin/reminders") {
                AdminScaffold("Reminders", onLogout = logout) { AdminRemindersScreen(adminVm) }
            }
            composable("admin/feedback")  {
                AdminScaffold("Feedback", onLogout = logout) { AdminFeedbackScreen(adminVm) }
            }
            composable("admin/broadcast") {
                AdminScaffold("Broadcast", onLogout = logout) { AdminBroadcastScreen(adminVm) }
            }
            composable("admin/memberships/new") {
                AdminScaffold("Create Membership", onLogout = logout) { AdminCreateMembershipScreen(adminVm) { navController.popBackStack() } }
            }
        }
    }
}

@Composable
private fun AdminScaffold(title: String, onLogout: (() -> Unit)? = null, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(NavyMid)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 16.sp)
            if (onLogout != null) {
                TextButton(onClick = onLogout) { Text("Logout", color = RedAlert, fontSize = 13.sp) }
            } else {
                Spacer(Modifier.width(56.dp))
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun StudentBottomBar(currentRoute: String, navController: androidx.navigation.NavHostController) {
    NavigationBar(containerColor = NavyMid) {
        studentNavItems.forEach { item ->
            val selected = currentRoute.startsWith(item.route)
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigate(item.route) { popUpTo(navController.graph.findStartDestination().id); launchSingleTop = true } },
                icon = { Icon(item.icon, item.label, modifier = Modifier.size(22.dp)) },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Amber, selectedTextColor = Amber, indicatorColor = AmberFaint, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("facilities") },
            icon = { Icon(Icons.Default.Info, "More", modifier = Modifier.size(22.dp)) },
            label = { Text("More", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Amber, selectedTextColor = Amber, indicatorColor = AmberFaint, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
        )
    }
}

@Composable
private fun AdminBottomBar(currentRoute: String, navController: androidx.navigation.NavHostController) {
    NavigationBar(containerColor = NavyMid) {
        adminNavItems.forEach { item ->
            val selected = currentRoute.startsWith(item.route)
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigate(item.route) { launchSingleTop = true } },
                icon = { Icon(item.icon, item.label, modifier = Modifier.size(22.dp)) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Amber, selectedTextColor = Amber, indicatorColor = AmberFaint, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
            )
        }
    }
}
