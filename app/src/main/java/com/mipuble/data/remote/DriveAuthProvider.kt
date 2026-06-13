package com.mipuble.data.remote

import android.app.PendingIntent
import android.content.Intent

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

    /**
     * Completes a consent flow: reads (and caches) the granted token from the
     * Intent returned by the consent Activity. Calling [authenticate] again
     * after a successful result must NOT re-prompt — that loop is the bug this
     * method exists to prevent.
     */
    suspend fun completeConsent(data: Intent?): AuthResult
}

/** Default no-credentials provider. */
class UnconfiguredDriveAuthProvider : DriveAuthProvider {
    override suspend fun authenticate(): AuthResult = AuthResult.Error
    override suspend fun completeConsent(data: Intent?): AuthResult = AuthResult.Error
}

class NeedConsentException(val intent: PendingIntent) : Exception("Consent required")
