package com.upbcientifica.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

        /*
         * Los dos campos desactivan autocorrección y mayúscula automática.
         * Sin esto, el teclado de un celular real puede "corregir" lo que se
         * escribe (poner mayúscula inicial, aceptar una sugerencia al cambiar
         * de campo) y el servidor recibe algo distinto de lo que se ve escrito.
         */
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        /*
         * Faltaba visualTransformation: la contraseña se mostraba en claro en
         * pantalla y, al no declararse como campo de contraseña, el teclado le
         * aplicaba autocorrección igual que a un texto normal.
         */
        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            // El usuario se recorta: un espacio al final, que el teclado móvil
            // añade con facilidad, haría fallar el login sin que se vea por qué.
            // La contraseña NO se recorta: un espacio puede ser parte de ella.
            onClick = { viewModel.login(usuario.trim(), contrasena) },
            modifier = Modifier.fillMaxWidth(),
            // Deshabilitado también con los campos vacíos: así no se manda una
            // petición que ya se sabe que va a fallar.
            enabled = state !is UiState.Loading &&
                usuario.isNotBlank() && contrasena.isNotBlank()
        ) {
            Text(if (state is UiState.Loading) "Ingresando..." else "Ingresar")
        }

        if (state is UiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((state as UiState.Error).message, color = MaterialTheme.colorScheme.error)
        }
    }
}
