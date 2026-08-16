package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upbcientifica.mobile.ui.state.UiState
import com.upbcientifica.mobile.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoginSuccess: () -> Unit) {
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is UiState.Success) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("UPB-CIENTÍFICA", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it },
            label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = contrasena, onValueChange = { contrasena = it },
            label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.login(usuario, contrasena) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is UiState.Loading
        ) {
            Text(if (state is UiState.Loading) "Ingresando..." else "Ingresar")
        }

        if (state is UiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((state as UiState.Error).message, color = MaterialTheme.colorScheme.error)
        }
    }
}
