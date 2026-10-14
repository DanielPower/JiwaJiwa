package ca.danielpower.jstudy.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import ca.danielpower.jstudy.BuildConfig
import ca.danielpower.jstudy.core.ClaudeException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Ask Android for a Google ID token addressed to the story server, for
 * [ca.danielpower.jstudy.data.Repository.signInToStories]. [context] must be
 * an activity, since Android shows its account sheet over it. Returns null if
 * you back out.
 */
suspend fun googleIdToken(context: Context): String? {
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    if (clientId.isBlank()) throw ClaudeException("Stories aren't set up in this build of the app.")
    val manager = CredentialManager.create(context)
    suspend fun ask(option: CredentialOption) =
        manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential

    val credential = try {
        try {
            // The account sheet, listing the Google accounts already on this phone.
            ask(GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(false).build())
        } catch (e: NoCredentialException) {
            // No Google account on the phone: Google's own sign-in screen lets you add one.
            ask(GetSignInWithGoogleOption.Builder(clientId).build())
        }
    } catch (e: GetCredentialCancellationException) {
        return null
    } catch (e: GetCredentialException) {
        throw ClaudeException("Google sign-in didn't work. Try again.")
    }
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw ClaudeException("Google sign-in didn't work. Try again.")
}

/** Signs in to stories, on the Stories screen and in Settings. */
@Composable
fun SignInWithGoogleButton(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PrimaryButton(onClick = { vm.signInToStories { googleIdToken(context) } }, modifier = modifier) {
        Box(
            Modifier.size(20.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text("Sign in with Google")
    }
}
