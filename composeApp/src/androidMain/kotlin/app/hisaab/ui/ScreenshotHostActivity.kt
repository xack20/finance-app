package app.hisaab.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import app.hisaab.AppContainer
import app.hisaab.auth.AuthRepository

/**
 * FragmentActivity host used **only** by debug-variant instrumented tests (the screenshot tour and
 * the E2E flow tests). The real [AppContainer] Android constructor registers ActivityResult launchers
 * (contact / image pickers) which must register in [onCreate] before the activity is STARTED, and an
 * activity launched by an instrumented test must live in the app package (not the test APK) — so the
 * container is built here. The test reads [container], then opens the DB / seeds / drives the UI.
 *
 * [authOverride] lets an E2E test inject a fake [AuthRepository] (set it BEFORE the activity launches,
 * e.g. in @BeforeClass) so the real onboarding flow can run with a fake OTP. Declared **only** in
 * src/debug/AndroidManifest.xml; never registered in the release build.
 */
class ScreenshotHostActivity : FragmentActivity() {

    lateinit var container: AppContainer
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val override = authOverride
        container = if (override != null) {
            AppContainer(applicationContext, this, override)
        } else {
            AppContainer(applicationContext, this)
        }
    }

    companion object {
        /** Set before launch to inject a fake AuthRepository for onboarding E2E. Null = real Supabase. */
        @Volatile
        var authOverride: AuthRepository? = null
    }
}
