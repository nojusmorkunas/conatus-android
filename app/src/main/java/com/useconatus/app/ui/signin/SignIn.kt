package com.useconatus.app.ui.signin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.useconatus.app.data.SessionRepository
import com.useconatus.app.ui.components.ConatusMark
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
private fun SignInFrame(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConatusMark(36.dp)
                content()
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun Problem(text: String?) {
    text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun BusyButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(48.dp), enabled = enabled && !busy, shape = MaterialTheme.shapes.medium) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(text)
    }
}

/** The first screen: use the app on its own, or connect it to a server. Either can change later in Settings. */
@Composable
fun WelcomeScreen(onLocal: () -> Unit, onConnect: () -> Unit) {
    SignInFrame {
        Text("Conatus", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Keep your tasks on this phone, or connect your Conatus server to sync them with the web app and your other devices. You can connect a server later.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onLocal, Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) { Text("Use on this phone") }
        OutlinedButton(onConnect, Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) { Text("Connect a server") }
    }
}

@Composable
fun ServerScreen(session: SessionRepository, previous: String?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf(previous.orEmpty()) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val askLocalNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        scope.launch {
            problem = if (granted) {
                session.chooseServer(address)
            } else {
                "This server is on your local network, and Android only lets Conatus reach it with the Nearby devices permission."
            }
            busy = false
        }
    }
    // Checked after network access, because finding out whether the server is local needs DNS.
    val connect = {
        scope.launch {
            if (session.needsLocalNetworkPermission(address)) {
                askLocalNetwork.launch(SessionRepository.LOCAL_NETWORK_PERMISSION)
            } else {
                problem = session.chooseServer(address)
                busy = false
            }
        }
    }
    val askInternet = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) {
            connect()
        } else {
            problem = "Android blocks Conatus from using the internet until you allow it."
            busy = false
        }
    }
    val submit = {
        if (!busy) {
            busy = true
            problem = null
            if (session.needsInternetPermission()) askInternet.launch(Manifest.permission.INTERNET) else connect()
        }
    }
    SignInFrame {
        Text("Connect to your server", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Conatus runs on your own server. Enter the address you use for the web app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            address,
            { address = it; problem = null },
            Modifier.fillMaxWidth(),
            label = { Text("Server address") },
            placeholder = { Text("conatus.example.com") },
            singleLine = true,
            isError = problem != null,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Problem(problem)
        if (denied) {
            val context = LocalContext.current
            TextButton({
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text("Open app settings") }
        }
        BusyButton("Continue", busy, enabled = address.isNotBlank(), onClick = submit)
        TextButton(onBack) { Text("Back") }
    }
}

@Composable
fun SignInScreen(session: SessionRepository, serverUrl: String, notice: String?, browserBusy: Boolean, browserProblem: String?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    val host = serverUrl.toHttpUrlOrNull()?.let { url -> url.host + (if (url.port != 80 && url.port != 443) ":${url.port}" else "") } ?: serverUrl
    val submit = {
        if (!busy && username.isNotBlank() && password.isNotEmpty()) {
            busy = true
            problem = null
            scope.launch {
                problem = session.signInWithPassword(username, password)
                busy = false
            }
        }
    }
    SignInFrame {
        Text("Sign in", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(host, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ scope.launch { session.forgetServer() } }) { Text("Change server") }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedTextField(
            username,
            { username = it; problem = null },
            Modifier.fillMaxWidth(),
            label = { Text("Username") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            password,
            { password = it; problem = null },
            Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            isError = problem != null,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Problem(problem)
        BusyButton("Sign in", busy, enabled = username.isNotBlank() && password.isNotEmpty(), onClick = submit)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text("or", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    problem = null
                    try {
                        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, session.browserSignInUri())
                    } catch (_: ActivityNotFoundException) {
                        problem = "There's no browser on this phone to sign in with."
                    }
                }
            },
            Modifier.fillMaxWidth().height(48.dp),
            enabled = !browserBusy,
            shape = MaterialTheme.shapes.medium,
        ) {
            if (browserBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in with your browser")
        }
        Text(
            "Use this for single sign-on, or to sign in with a password saved in your browser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Problem(browserProblem)
        Spacer(Modifier.height(8.dp))
    }
}
