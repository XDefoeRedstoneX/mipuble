package com.mipuble.data.remote

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Real Google Drive auth via the Play Services Authorization API.
 *
 * Requests two scopes: drive.readonly (list/download everything in the mipuble
 * folder, including web-added files) and drive.file (create the folder and
 * upload). drive.file is non-sensitive, so it adds no verification burden.
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
            .setRequestedScopes(
                listOf(
                    Scope("https://www.googleapis.com/auth/drive.readonly"),
                    Scope("https://www.googleapis.com/auth/drive.file"),
                ),
            )
            .build()

    override suspend fun authenticate(): AuthResult {
        cachedToken?.let { return AuthResult.Success(it) }
        return suspendCancellableCoroutine { cont ->
            authorizationClient.authorize(request)
                .addOnSuccessListener { cont.resume(it.toAuthResult()) }
                .addOnFailureListener { cont.resume(AuthResult.Error) }
        }
    }

    override suspend fun completeConsent(data: Intent?): AuthResult = try {
        authorizationClient.getAuthorizationResultFromIntent(data).toAuthResult()
    } catch (e: ApiException) {
        AuthResult.Error
    }

    private fun AuthorizationResult.toAuthResult(): AuthResult = when {
        hasResolution() -> AuthResult.ConsentRequired(pendingIntent!!)
        accessToken != null -> {
            cachedToken = accessToken
            AuthResult.Success(accessToken!!)
        }
        else -> AuthResult.Error
    }
}
