package com.mipuble.data.remote

import android.app.PendingIntent

/**
 * Result of an authentication attempt.
 */
sealed interface AuthResult {
    data class Success(val token: String) : AuthResult
    data class ConsentRequired(val intent: PendingIntent) : AuthResult
    data object Error : AuthResult
}

/**
 * Supplies a Google OAuth access token with Drive read scope.
 */
interface DriveAuthProvider {
    suspend fun authenticate(): AuthResult
}

/** Default no-credentials provider. */
class UnconfiguredDriveAuthProvider : DriveAuthProvider {
    override suspend fun authenticate(): AuthResult = AuthResult.Error
}

class NeedConsentException(val intent: PendingIntent) : Exception("Consent required")
