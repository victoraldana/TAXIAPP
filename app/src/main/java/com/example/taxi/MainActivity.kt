package com.example.taxi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taxi.model.UserRole
import com.example.taxi.ui.ClientSearchScreen
import com.example.taxi.ui.DriverHomeScreen
import com.example.taxi.ui.LoginScreen
import com.example.taxi.ui.RegisterScreen
import com.example.taxi.ui.theme.TAXITheme
import com.example.taxi.viewmodel.AuthViewModel
import com.example.taxi.viewmodel.TaxiViewModel
import com.google.android.libraries.places.api.Places

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
    val navController: NavHostController = rememberNavController()
    val taxiViewModel: TaxiViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()

    NavHost(navController = navController, startDestination = "login") {

        // ── Login por teléfono ────────────────────────────────────────────────
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { role ->
                    val dest = if (role == "driver") "driver_home" else "client_search"
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
            // Por defecto CLIENT; el usuario podrá cambiar su rol en el futuro
            RegisterScreen(
                viewModel = authViewModel,
                selectedRole = UserRole.CLIENT,
                onRegistrationComplete = {
                    navController.navigate("client_search") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ── Pantalla del Cliente ──────────────────────────────────────────────
        composable("client_search") {
            ClientSearchScreen(viewModel = taxiViewModel)
        }

        // ── Pantalla del Conductor ────────────────────────────────────────────
        composable("driver_home") {
            DriverHomeScreen()
        }
    }
}