package com.example.taxi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taxi.model.UserRole
import com.example.taxi.ui.ClientSearchScreen
import com.example.taxi.ui.DriverHomeScreen
import com.example.taxi.ui.RoleSelectionScreen
import com.example.taxi.ui.theme.TAXITheme
import com.example.taxi.viewmodel.TaxiViewModel
import com.google.android.libraries.places.api.Places

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Google Places SDK
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCPjoBbZxLwA0di-wiYHtoaVwF0lktoLmM")
        }

        enableEdgeToEdge()
        setContent {
            TAXITheme {
                val navController = rememberNavController()
                val viewModel: TaxiViewModel = viewModel()

                NavHost(navController = navController, startDestination = "role_selection") {
                    composable("role_selection") {
                        RoleSelectionScreen(onRoleSelected = { role ->
                            viewModel.selectRole(role)
                            if (role == UserRole.CLIENT) {
                                navController.navigate("client_search")
                            } else {
                                navController.navigate("driver_home")
                            }
                        })
                    }
                    composable("client_search") {
                        ClientSearchScreen(viewModel = viewModel)
                    }
                    composable("driver_home") {
                        DriverHomeScreen()
                    }
                }
            }
        }
    }
}