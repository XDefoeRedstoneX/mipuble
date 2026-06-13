package com.mipuble.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipuble.BuildConfig
import com.mipuble.ui.reader.FontPickerRow
import com.mipuble.ui.reader.PageTurnModeRow
import com.mipuble.ui.reader.StepperRow
import com.mipuble.ui.reader.ThemeRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDriveGuide by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    // Drive consent launcher (so Sync / Reset can grant access from here too).
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentResult(result.data)
        } else {
            viewModel.onConsentCanceled(result.resultCode)
        }
    }
    LaunchedEffect(uiState.pendingConsent) {
        val consent = uiState.pendingConsent
        if (consent != null) {
            try {
                authLauncher.launch(IntentSenderRequest.Builder(consent).build())
            } catch (e: Exception) {
                viewModel.onConsentLaunchFailed(e.message)
            } finally {
                viewModel.onConsentShown()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionTitle("Reading defaults")
            Text(
                "These apply to every book; the reader's own panel changes them too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemeRow(
                selected = uiState.preferences.theme,
                onSelect = viewModel::onThemeSelected,
            )
            FontPickerRow(
                selected = uiState.preferences.font,
                onSelect = viewModel::onFontSelected,
            )
            PageTurnModeRow(
                selected = uiState.preferences.pageTurnMode,
                onSelect = viewModel::onPageTurnModeSelected,
            )
            StepperRow(
                label = "Text size",
                value = "${uiState.preferences.fontScalePercent}%",
                onDecrease = { viewModel.onStepFont(-1) },
                onIncrease = { viewModel.onStepFont(+1) },
            )
            StepperRow(
                label = "Line spacing",
                value = "%.1f".format(uiState.preferences.lineSpacingPercent / 100f),
                onDecrease = { viewModel.onStepLineSpacing(-1) },
                onIncrease = { viewModel.onStepLineSpacing(+1) },
            )

            HorizontalDivider()

            SectionTitle("Google Drive")
            Text(
                if (uiState.remoteAvailable) "Connected to Google Drive" else "Not connected",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Books sync from the \"mipuble\" folder in your Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::onSyncNow, enabled = !uiState.isSyncing) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Sync now")
                }
                OutlinedButton(onClick = { showDriveGuide = true }) {
                    Text("Setup help…")
                }
            }

            // Live progress for an upload batch or a running reset.
            uiState.upload?.let { up ->
                Text(
                    "Uploading ${up.currentIndex}/${up.total}: ${up.fileName}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(progress = { up.fraction }, modifier = Modifier.fillMaxWidth())
            }

            OutlinedButton(
                onClick = { showReset = true },
                enabled = !uiState.isResetting && uiState.upload == null,
            ) {
                if (uiState.isResetting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Reset library to Drive…")
            }

            HorizontalDivider()

            SectionTitle("Storage")
            Text(
                "${uiState.downloadedCount} book(s) downloaded on this device",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = viewModel::onRemoveAllDownloads,
                enabled = uiState.evictableIds.isNotEmpty(),
            ) {
                Text("Remove all downloads (keep books in library)")
            }

            HorizontalDivider()

            SectionTitle("About")
            Text("mipuble ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "A native EPUB reader. Natural sorting, ±1% brightness, " +
                    "custom shelves, and a metadata-only cloud library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
        }
    }

    if (showDriveGuide) {
        DriveSetupGuideDialog(onDismiss = { showDriveGuide = false })
    }

    if (showReset) {
        ResetToDriveDialog(
            localOnlyCount = uiState.localOnlyCount,
            onConfirm = { uploadFirst ->
                showReset = false
                viewModel.onResetToDrive(uploadFirst)
            },
            onDismiss = { showReset = false },
        )
    }
}

/**
 * Confirms the destructive reset: the local library is replaced by the Drive
 * folder's contents. Local-only books can be uploaded first so nothing is lost.
 */
@Composable
private fun ResetToDriveDialog(
    localOnlyCount: Int,
    onConfirm: (uploadFirst: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var uploadFirst by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset library to Drive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your library will be replaced with the books in your Drive " +
                        "\"mipuble\" folder. Reading progress and categories are kept by file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (localOnlyCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uploadFirst, onCheckedChange = { uploadFirst = it })
                        Text(
                            "Upload $localOnlyCount device-only book(s) first",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(uploadFirst) }) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/**
 * Connecting a personal Google account requires an OAuth client id that only
 * the app's developer can create, so the in-app "Connect" is a guide rather
 * than a button that can't work. Full walkthrough: docs/GOOGLE_DRIVE_SETUP.md.
 */
@Composable
private fun DriveSetupGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Google Drive") },
        text = {
            Text(
                """
                The app currently uses a built-in offline demo library. Connecting your real Google Drive is a one-time developer setup:

                1. Create a (free) project at console.cloud.google.com and enable the Google Drive API.
                2. Create an Android OAuth client ID for package com.mipuble with your signing key's SHA-1.
                3. Implement DriveAuthProvider using Credential Manager and swap one binding in RemoteModule.

                The step-by-step guide with commands and code lives in the repository: docs/GOOGLE_DRIVE_SETUP.md
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}
