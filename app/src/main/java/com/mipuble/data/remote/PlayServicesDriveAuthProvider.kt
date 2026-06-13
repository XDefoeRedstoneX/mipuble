package com.mipuble.data.remote

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class PlayServicesDriveAuthProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriveAuthProvider {

    override suspend fun authenticate(): AuthResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.readonly")))
            .build()
        
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                when {
                    result.hasResolution() -> {
                        cont.resume(AuthResult.ConsentRequired(result.pendingIntent!!))
                    }
                    result.accessToken != null -> {
                        cont.resume(AuthResult.Success(result.accessToken!!))
                    }
                    else -> {
                        cont.resume(AuthResult.Error)
                    }
                }
            }
            .addOnFailureListener { 
                cont.resume(AuthResult.Error)
            }
    }
}
