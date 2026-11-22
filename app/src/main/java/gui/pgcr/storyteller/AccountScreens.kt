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
import androidx.compose.runtime.LaunchedEffect
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

/** Tela própria para criar uma conta Lyron (nome de usuário, apelido, e-mail, senha e confirmação). */
@Composable
internal fun CreateAccountScreen(
    authBusy: Boolean,
    onCheckUsername: suspend (String) -> Boolean?,
    onCreateAccount: (email: String, password: String, username: String, nickname: String) -> Unit,
    onHaveAccount: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var usernameCheck by remember { mutableStateOf(UsernameCheck.IDLE) }

    // Mesma verificação do perfil: formato + disponibilidade (espera a pessoa parar de digitar).
    LaunchedEffect(username) {
        val candidate = username.trim()
        usernameCheck = when {
            candidate.isEmpty() -> UsernameCheck.IDLE
            !USERNAME_REGEX.matches(candidate) -> UsernameCheck.INVALID
            else -> UsernameCheck.CHECKING
        }
        if (usernameCheck == UsernameCheck.CHECKING) {
            kotlinx.coroutines.delay(500)
            usernameCheck = when (onCheckUsername(candidate)) {
                true -> UsernameCheck.AVAILABLE
                false -> UsernameCheck.TAKEN
                null -> UsernameCheck.UNKNOWN
            }
        }
    }

    val usernameOk = usernameCheck == UsernameCheck.AVAILABLE || usernameCheck == UsernameCheck.UNKNOWN
    val nicknameOk = nickname.trim().isNotEmpty()
    val emailOk = email.trim().contains("@")
    val passwordOk = password.length >= AUTH_MIN_PASSWORD
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSubmit = !authBusy && usernameOk && nicknameOk && emailOk && passwordOk && confirm == password

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
            value = username,
            onValueChange = { username = normalizeUsername(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome de usuário") },
            prefix = { Text("@") },
            singleLine = true,
            isError = usernameCheck == UsernameCheck.TAKEN || usernameCheck == UsernameCheck.INVALID,
            supportingText = {
                Text(
                    when (usernameCheck) {
                        UsernameCheck.INVALID -> "Use de 3 a 20 caracteres: a–z, 0–9 e _."
                        UsernameCheck.CHECKING -> "Verificando disponibilidade…"
                        UsernameCheck.AVAILABLE -> "Disponível ✓"
                        UsernameCheck.TAKEN -> "Esse nome de usuário já está em uso."
                        UsernameCheck.UNKNOWN -> "Não deu para verificar agora; conferimos ao criar a conta."
                        else -> "Único e público (@seunome). 3 a 20 caracteres: a–z, 0–9 e _"
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it.take(30) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Apelido") },
            singleLine = true,
            supportingText = { Text("Como você aparece para os outros. Pode mudar depois no perfil.") }
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
            label = { Text("Senha (mín. $AUTH_MIN_PASSWORD)") },
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
            onClick = { onCreateAccount(email.trim(), password, username.trim(), nickname.trim()) },
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
