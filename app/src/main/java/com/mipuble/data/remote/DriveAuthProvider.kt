package com.mipuble.data.remote

/**
 * Supplies a Google OAuth access token with Drive read scope. The concrete
 * implementation would obtain this via Credential Manager / Google Sign-In and
 * an OAuth client id configured in a Google Cloud project — the one piece that
 * needs real credentials. Returning null means "not signed in".
 */
interface DriveAuthProvider {
    suspend fun accessToken(): String?
}

/** Default no-credentials provider; the app uses the fake source until this is wired. */
class UnconfiguredDriveAuthProvider : DriveAuthProvider {
    override suspend fun accessToken(): String? = null
}
