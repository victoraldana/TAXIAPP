package com.example.taxi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taxi.model.UserRole
import com.example.taxi.network.RetrofitClient
import com.example.taxi.ui.ClientSearchScreen
import com.example.taxi.ui.DriverHomeScreen
import com.example.taxi.ui.HomeScreen
import com.example.taxi.ui.LoginScreen
import com.example.taxi.ui.RegisterScreen
import com.example.taxi.ui.theme.TAXITheme
import com.example.taxi.viewmodel.AuthViewModel
import com.example.taxi.viewmodel.DriverViewModel
import com.example.taxi.viewmodel.TaxiViewModel
import com.google.android.libraries.places.api.Places
import androidx.compose.ui.platform.LocalContext
import com.example.taxi.utils.SessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        enableEdgeToEdge()
        setContent {
            TAXITheme {
                TaxiNavGraph()
            }
        }
    }
}

@Composable
fun TaxiNavGraph() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    val navController: NavHostController = rememberNavController()
    val taxiViewModel: TaxiViewModel     = viewModel()
    val authViewModel: AuthViewModel     = viewModel()
    val driverViewModel: DriverViewModel = viewModel()

    // ── Estado de arranque: calculamos destino inicial de forma asíncrona ─────
    // Para el cliente necesitamos saber si tiene un viaje activo antes de decidir
    // si navegamos a "home" o directamente a "client_search".
    var startDest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val user = sessionManager.getUser()
        if (user == null) {
            startDest = "login"
            return@LaunchedEffect
        }
        authViewModel.setLoggedUser(user)

        if (user.role == "driver") {
            startDest = "driver_home"
        } else {
            // Para el cliente: verificar si tiene viaje activo en el backend
            val dest = try {
                val res = RetrofitClient.apiService.getActiveTripForClient(user.id)
                if (res.isSuccessful && res.body()?.data != null) {
                    "client_search"  // Hay viaje activo → ir directo a la pantalla del viaje
                } else {
                    "home"
                }
            } catch (e: Exception) {
                "home"  // En caso de error de red, ir a home normalmente
            }
            startDest = dest
        }
    }

    // Mostrar pantalla de carga hasta que sepamos a dónde ir
    if (startDest == null) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color(0xFF0F1923)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFFFC107))
        }
        return
    }

    NavHost(navController = navController, startDestination = startDest!!) {

        // ── Login ─────────────────────────────────────────────────────────────
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { user, tokens ->
                    sessionManager.saveSession(user, tokens)
                    authViewModel.setLoggedUser(user)
                    val dest = if (user.role == "driver") "driver_home" else "home"
                    navController.navigate(dest) {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGoToRegister = {
                    navController.navigate("register")
                }
            )
        }

        // ── Registro multi-paso ───────────────────────────────────────────────
        composable("register") {
            RegisterScreen(
                viewModel = authViewModel,
                selectedRole = UserRole.CLIENT,
                onRegistrationComplete = { user, tokens ->
                    sessionManager.saveSession(user, tokens)
                    authViewModel.setLoggedUser(user)
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Home del cliente ───────────────────────────────────────────────────
        composable("home") {
            val loggedUser by authViewModel.loggedUser.collectAsState()
            HomeScreen(
                userName = loggedUser?.fullName ?: loggedUser?.phone ?: "Usuario",
                clientId = loggedUser?.id ?: "",
                onLogout = {
                    sessionManager.clearSession()
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onServiceSelected = { serviceId ->
                    when (serviceId) {
                        "trip" -> navController.navigate("client_search")
                        else   -> { /* Próximamente */ }
                    }
                }
            )
        }

        // ── Mapa de viaje ─────────────────────────────────────────────────────
        composable("client_search") {
            val loggedUser by authViewModel.loggedUser.collectAsState()
            ClientSearchScreen(
                viewModel      = taxiViewModel,
                clientId       = loggedUser?.id ?: "",
                onTripFinished = {
                    navController.navigate("home") {
                        popUpTo("client_search") { inclusive = true }
                    }
                }
            )
        }

        // ── App del conductor ─────────────────────────────────────────────────
        composable("driver_home") {
            val loggedUser by authViewModel.loggedUser.collectAsState()
            loggedUser?.let { user ->
                DriverHomeScreen(
                    driver = user,
                    viewModel = driverViewModel,
                    onLogout = {
                        sessionManager.clearSession()
                        authViewModel.logout()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}