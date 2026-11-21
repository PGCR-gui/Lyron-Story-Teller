package gui.pgcr.storyteller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tela própria para criar uma conta Lyron (e-mail + senha + confirmação). */
@Composable
internal fun CreateAccountScreen(
    authBusy: Boolean,
    onCreateAccount: (email: String, password: String) -> Unit,
    onHaveAccount: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val emailOk = email.trim().contains("@")
    val passwordOk = password.length >= 6
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSubmit = !authBusy && emailOk && passwordOk && confirm == password

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Criar conta Lyron", color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Crie sua conta para publicar histórias online e sincronizar seu catálogo.",
            color = SecondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Senha (mín. 6)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirmar senha") },
            singleLine = true,
            isError = mismatch,
            supportingText = if (mismatch) ({ Text("As senhas não coincidem.") }) else null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Button(
            onClick = { onCreateAccount(email.trim(), password) },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (authBusy) "Aguarde…" else "Criar conta")
        }
        OutlinedButton(
            onClick = onHaveAccount,
            enabled = !authBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Já tenho conta", color = Accent)
        }
        Spacer(Modifier.height(12.dp))
    }
}
