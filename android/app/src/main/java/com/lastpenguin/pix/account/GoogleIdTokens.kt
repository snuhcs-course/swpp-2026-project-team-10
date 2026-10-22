// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

enum class GoogleSignInProblem {
    /** The person closed Google's sheet: nothing to show. */
    CANCELLED,

    /** The phone has no Google account to choose. */
    NO_ACCOUNT,
    FAILED,
}

class GoogleSignInException(val problem: GoogleSignInProblem, cause: Throwable? = null) :
    Exception(problem.name, cause)

/** A Google ID token for the Pix server, which verifies it and keeps only Google's user id (AD-12). */
interface GoogleIdTokens {
    /** Shows Google's account chooser over [context], an Activity. Fails with a [GoogleSignInException]. */
    suspend fun request(context: Context): Result<String>
}

/**
 * Google's *Sign in with Google* through Credential Manager (Design 1.2). The token is addressed to the web client
 * [serverClientId], the `PIX_GOOGLE_CLIENT_ID` of both servers; Google accepts the request only from an APK whose
 * signing key's SHA-1 is registered as an Android client in the same Google Cloud project (Design 2.8, Configuration).
 * Owner: Accounts (#54).
 */
class CredentialManagerGoogleIdTokens(private val serverClientId: String) : GoogleIdTokens {

    override suspend fun request(context: Context): Result<String> {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.failure(GoogleSignInException(GoogleSignInProblem.FAILED))
            }
        } catch (error: GetCredentialCancellationException) {
            Result.failure(GoogleSignInException(GoogleSignInProblem.CANCELLED, error))
        } catch (error: NoCredentialException) {
            Result.failure(GoogleSignInException(GoogleSignInProblem.NO_ACCOUNT, error))
        } catch (error: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed: ${error.type}", error)
            Result.failure(GoogleSignInException(GoogleSignInProblem.FAILED, error))
        } catch (error: GoogleIdTokenParsingException) {
            Log.w(TAG, "Google sign-in returned an unreadable token", error)
            Result.failure(GoogleSignInException(GoogleSignInProblem.FAILED, error))
        }
    }

    private companion object {
        const val TAG = "PixAccount"
    }
}
