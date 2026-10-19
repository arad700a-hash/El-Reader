package com.novelreader.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import com.novelreader.app.data.ThemeMode
import com.novelreader.app.reader.ReaderActivity
import com.novelreader.app.ui.NovelTheme
import com.novelreader.app.ui.library.LibraryEvent
import com.novelreader.app.ui.library.LibraryScreen
import com.novelreader.app.ui.library.LibraryViewModel
import com.novelreader.app.ui.library.epubPickerMimeTypes

/**
 * Library screen and the entry point for "Open with" / "Share" of EPUB files from other apps.
 * External files are copied to private storage first (the temporary read grant of a content:// URI can vanish),
 * then opened directly in the reader; the user decides there whether to add them to the library.
 */
class MainActivity : AppCompatActivity() {

    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val settings = remember { graph.settings }
            val theme = settings.readerSettings.collectAsState(initial = com.novelreader.app.data.ReaderSettings()).value.theme
            val snackbar = remember { SnackbarHostState() }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                vm.importPicked(uris)
            }

            LaunchedEffect(Unit) {
                vm.events.collect { e ->
                    when (e) {
                        is LibraryEvent.OpenBook -> startActivity(ReaderActivity.intent(this@MainActivity, e.bookId))
                        is LibraryEvent.Message ->
                            snackbar.showSnackbar(e.arg?.let { getString(e.resId, it) } ?: getString(e.resId))
                    }
                }
            }

            NovelTheme(dark = theme == ThemeMode.DARK) {
                LibraryScreen(
                    vm = vm,
                    onOpenBook = { startActivity(ReaderActivity.intent(this, it)) },
                    onPickBooks = { picker.launch(epubPickerMimeTypes()) },
                    snackbar = snackbar,
                )
            }
        }

        // Only the first creation handles the launching intent; rotations must not re-import it.
        if (savedInstanceState == null) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val uri = incomingUri(intent) ?: return
        // Consume it so a later recreation does not process the same file again.
        intent?.data = null
        intent?.removeExtra(Intent.EXTRA_STREAM)
        vm.openExternal(uri)
    }

    private fun incomingUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        else -> null
    }.also { uri ->
        if (uri == null && (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND)) {
            Toast.makeText(this, R.string.import_err_no_access, Toast.LENGTH_LONG).show()
        }
    }
}
