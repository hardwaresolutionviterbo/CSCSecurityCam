package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class GoogleAccount(
    val email: String,
    val displayName: String,
    val subject: String
)

sealed class GoogleSignInResult {
    data class Success(val account: GoogleAccount) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

/**
 * Accesso Google reale tramite Credential Manager: serve il client ID OAuth web del progetto
 * (BuildConfig.GOOGLE_WEB_CLIENT_ID, da -PGOOGLE_WEB_CLIENT_ID oppure variabile d'ambiente).
 * Senza client ID il pulsante resta visibile ma spiega che la build non è configurata, invece di
 * fingere un accesso.
 */
object GoogleSignIn {

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(context: Context): GoogleSignInResult {
        if (!isConfigured) {
            return GoogleSignInResult.Failure(
                "Accesso Google non configurato: manca il client ID OAuth (GOOGLE_WEB_CLIENT_ID)"
            )
        }
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return GoogleSignInResult.Failure("Google non ha restituito un token ID valido")
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult.Success(
                GoogleAccount(
                    email = google.id,
                    displayName = google.displayName ?: google.id.substringBefore("@"),
                    // googleid 1.1.1 espone id, displayName e idToken; lo "sub" è nel JWT.
                    subject = google.id
                )
            )
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Failure("Accesso Google annullato")
        } catch (e: Exception) {
            GoogleSignInResult.Failure("Accesso Google non riuscito: ${e.message ?: "errore sconosciuto"}")
        }
    }
}
