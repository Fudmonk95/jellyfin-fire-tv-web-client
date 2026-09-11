from pathlib import Path
import shutil

def replace(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise RuntimeError("Upstream anchor missing: " + path)
    p.write_text(text.replace(old, new, 1))

replace("app/build.gradle.kts", '    defaultConfig {', '    defaultConfig {\n        applicationId = "uk.co.cyberscott.jellyfinwebtv"')
replace("app/src/main/AndroidManifest.xml", '    <queries>', '''    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <queries>''')
replace("app/src/main/AndroidManifest.xml", 'android:allowBackup="true"', 'android:allowBackup="true"\n        android:banner="@drawable/tv_banner"')
replace("app/src/main/AndroidManifest.xml", 'android:launchMode="singleTask"', 'android:launchMode="singleTask"\n            android:screenOrientation="landscape"')
replace("app/src/main/AndroidManifest.xml", '<category android:name="android.intent.category.LAUNCHER" />', '<category android:name="android.intent.category.LAUNCHER" />\n                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />')
replace("app/src/main/assets/native/injectionScript.js", "'/native/EventEmitter.js',", "'/native/EventEmitter.js',\n        '/native/TvNavigation.js',")
replace("app/src/main/java/org/jellyfin/mobile/webapp/WebViewFragment.kt", '        settings.applyDefault()', '        settings.applyDefault()\n        TvRemoteController(this).attach()')
replace("app/src/main/res/values/strings_donottranslate.xml", '>Jellyfin</string>', '>Jellyfin Web TV</string>')
replace("app/src/debug/res/values/strings_donottranslate.xml", '>Jellyfin Debug</string>', '>Jellyfin Web TV Debug</string>')
shutil.copyfile("recovery/TvRemoteController.kt", "app/src/main/java/org/jellyfin/mobile/webapp/TvRemoteController.kt")
shutil.copyfile("recovery/TvNavigation.js", "app/src/main/assets/native/TvNavigation.js")
Path("app/src/main/res/drawable/tv_banner.xml").write_text('''<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
<item><shape><solid android:color="#101419"/><size android:width="320dp" android:height="180dp"/></shape></item>
<item android:drawable="@drawable/app_logo" android:width="252dp" android:height="72dp" android:gravity="center"/>
</layer-list>''')
readme = Path("README.md")
readme.write_text("""# Jellyfin Web TV — experimental prototype

Full server-provided Jellyfin Web interface with a Fire TV remote navigation layer.
Plugin compatibility and performance are not yet device-tested. This is an unofficial
fork of Jellyfin Android; its native player, authentication and server setup are retained.
The app does not guarantee that all web plugins work.

Recovered from the earlier prototype against upstream commit
44a9a67a203a022546ebff7cb4963d4189a572db. D-pad/select handling is restored;
Back uses the upstream handler. Additional media-key polish and input-field testing
remain outstanding. No performance guarantee or stable release is claimed.

Build: JDK 17, Android SDK, then ./gradlew assembleLibreDebug.
APK: app/build/outputs/apk/libre/debug/.
The debug APK is for testing; CI debug signing keys may differ between builds.

Upstream documentation and license follow.

---

""" + readme.read_text())
