package com.mipuble.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
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
 * Diagnostic logging is under the tag "MipubleDrive" so the consent flow can be
 * traced from Logcat without a debugger.
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
        cachedToken?.let {
            Log.i(TAG, "authenticate(): using cached token")
            return AuthResult.Success(it)
        }
        return suspendCancellableCoroutine { cont ->
            authorizationClient.authorize(request)
                .addOnSuccessListener {
                    Log.i(
                        TAG,
                        "authorize() ok: hasResolution=${it.hasResolution()} hasToken=${it.accessToken != null}",
                    )
                    cont.resume(it.toAuthResult())
                }
                .addOnFailureListener { e ->
                    val code = (e as? ApiException)?.statusCode
                    Log.e(TAG, "authorize() FAILED statusCode=$code", e)
                    cont.resume(AuthResult.Error)
                }
        }
    }

    override suspend fun completeConsent(data: Intent?): AuthResult = try {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        Log.i(
            TAG,
            "completeConsent(): hasResolution=${result.hasResolution()} hasToken=${result.accessToken != null}",
        )
        result.toAuthResult()
    } catch (e: ApiException) {
        Log.e(TAG, "completeConsent() FAILED statusCode=${e.statusCode}", e)
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

    private companion object {
        const val TAG = "MipubleDrive"
    }
}
