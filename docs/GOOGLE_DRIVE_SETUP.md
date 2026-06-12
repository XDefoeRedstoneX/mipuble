# Connecting mipuble to your real Google Drive

Out of the box, mipuble's cloud library runs against a bundled **offline demo
source** so the app works with zero setup. To make it list and download EPUBs
from *your* Google Drive, you need to do a one-time developer setup. This
guide assumes no prior Google Cloud experience.

> **Why isn't this just a login button?** Google requires every app that
> touches Drive to identify itself with an *OAuth client ID* created by the
> app's developer in Google Cloud Console, tied to the app's package name and
> signing key. There is no way to ship a working client ID in a public
> repository — every person who builds the app signs it with their own key,
> so every builder creates their own (free) client ID.

## Part 1 — Google Cloud Console (≈10 minutes, free)

1. Go to <https://console.cloud.google.com> and sign in with any Google
   account.
2. Top bar ▸ project selector ▸ **New project**. Name it anything (e.g.
   `mipuble-drive`) and create it.
3. With the project selected: **APIs & Services ▸ Library** ▸ search
   **Google Drive API** ▸ **Enable**.
4. **APIs & Services ▸ OAuth consent screen**:
   - User type: **External**, then *Create*.
   - Fill only the required fields (app name `mipuble`, your email twice).
   - Scopes: add **`.../auth/drive.readonly`**.
   - Test users: **add your own Gmail address** (while the app is in
     "Testing" status, only listed test users can sign in — that's you).
5. **APIs & Services ▸ Credentials ▸ Create credentials ▸ OAuth client ID**:
   - Application type: **Android**.
   - Package name: `com.mipuble`
   - SHA-1: your debug signing key's fingerprint. Get it with:

     ```bash
     ./gradlew signingReport
     # copy the SHA1 line under "Variant: debug"
     ```

     (or `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`)
   - Create. You don't need to copy anything — for Android clients Google
     matches your app by package + SHA-1 automatically.

## Part 2 — Wire the app (≈20 minutes)

### 1. Add the auth dependencies

`gradle/libs.versions.toml`:

```toml
[versions]
credentials = "1.3.0"
googleid = "1.1.1"
playServicesAuth = "21.2.0"

[libraries]
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
androidx-credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }
```

`app/build.gradle.kts` dependencies:

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services)
implementation(libs.googleid)
implementation(libs.play.services.auth)
```

### 2. Implement `DriveAuthProvider`

The app already defines the seam (`data/remote/DriveAuthProvider.kt`); you
provide a real implementation that returns an OAuth **access token** with the
`drive.readonly` scope. The shortest path uses Play Services' Authorization
API:

```kotlin
class PlayServicesDriveAuthProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriveAuthProvider {

    override suspend fun accessToken(): String? = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.readonly")))
            .build()
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                // If result.hasResolution(), launch result.pendingIntent from an
                // Activity once to let the user consent, then call again.
                cont.resume(result.accessToken)
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
```

(The first call returns a `pendingIntent` you must launch from an Activity so
the user can pick an account and consent; after that it resolves silently.)

### 3. Swap two bindings in `data/di/RemoteModule.kt`

```kotlin
// before
fun provideDriveAuthProvider(): DriveAuthProvider = UnconfiguredDriveAuthProvider()
abstract fun bindRemoteLibrarySource(impl: FakeRemoteLibrarySource): RemoteLibrarySource

// after
fun provideDriveAuthProvider(impl: PlayServicesDriveAuthProvider): DriveAuthProvider = impl
abstract fun bindRemoteLibrarySource(impl: DriveRemoteLibrarySource): RemoteLibrarySource
```

### 4. Run it

Build and run on a device signed in to the Google account you added as a
test user. Tap **↻ Sync** — your Drive EPUBs (`mimeType =
application/epub+zip`) appear as metadata-only books; tap one to download.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `Sync failed` immediately | Token is null — consent not completed, or account isn't in the consent screen's test users |
| `Drive list failed: 403` | Drive API not enabled on the project, or wrong scope |
| Sign-in dialog never appears | SHA-1 in the OAuth client doesn't match your debug keystore (re-run `./gradlew signingReport`) |
| Books missing | Files aren't `application/epub+zip` in Drive (re-upload, don't convert to Google Docs) |
