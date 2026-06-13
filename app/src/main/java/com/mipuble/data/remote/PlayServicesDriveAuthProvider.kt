package com.mipuble.data.remote

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Real Google Drive auth via the Play Services Authorization API.
 *
 * The flow:
 *  1. [authenticate] calls authorize(); a first run returns a pendingIntent
 *     ([AuthResult.ConsentRequired]) for the UI to launch.
 *  2. After the user consents, the UI passes the result Intent to
 *     [completeConsent], which reads the granted token via
 *     getAuthorizationResultFromIntent and *caches* it.
 *  3. Subsequent [authenticate] calls return the cached token without
 *     re-authorizing — which is what stops the "consent required" loop that
 *     blind re-authorization produced.
 *
 * The cached token is in-memory only (~1h lifetime). On a fresh process the
 * cache is empty, but authorize() then returns the already-granted token
 * silently (no consent prompt), so the user isn't asked again.
 */
@Singleton
class PlayServicesDriveAuthProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriveAuthProvider {

    @Volatile
    private var cachedToken: String? = null

    private val authorizationClient by lazy { Identity.getAuthorizationClient(context) }

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.readonly")))
            .build()

    override suspend fun authenticate(): AuthResult {
        cachedToken?.let { return AuthResult.Success(it) }
        return suspendCancellableCoroutine { cont ->
            authorizationClient.authorize(request)
                .addOnSuccessListener { cont.resume(it.toAuthResult()) }
                .addOnFailureListener { cont.resume(AuthResult.Error) }
        }
    }

    override suspend fun completeConsent(data: Intent?): AuthResult =
        runCatching { authorizationClient.getAuthorizationResultFromIntent(data).toAuthResult() }
            .getOrDefault(AuthResult.Error)

    private fun AuthorizationResult.toAuthResult(): AuthResult = when {
        hasResolution() -> AuthResult.ConsentRequired(pendingIntent!!)
        accessToken != null -> {
            cachedToken = accessToken
            AuthResult.Success(accessToken!!)
        }
        else -> AuthResult.Error
    }
}
