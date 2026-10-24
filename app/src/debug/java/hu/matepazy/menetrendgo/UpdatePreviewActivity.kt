package hu.matepazy.menetrendgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope

/** Synthetic release UI fixture. Included in debug builds only; never publishable metadata. */
class UpdatePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID.endsWith(".updatepreview")) { "Use -PupdatePreview for isolated UI fixtures" }
        enableEdgeToEdge()
        val updates = UpdateController(applicationContext, lifecycleScope)
        updates.chooseEnabled(false)
        updates.select(UpdateState.UpdateAvailable(
            version = "v1.1.0-beta.1",
            notes = "Szemléltető tesztkiadás, nem valódi frissítés.\n\n• Gyorsabb indulás\n• Javított menetrendi megjelenés\n\nA teszt letöltése szándékosan hibával végződik.",
            downloadUrl = "https://example.invalid/menetrendgo-v1.1.0-beta.1.apk",
            isBeta = true,
        ))
        setContent { MenetrendTheme("system") { UpdateHost(updates) } }
    }
}
