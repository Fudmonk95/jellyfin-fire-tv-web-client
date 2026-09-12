package org.jellyfin.androidtv.ui.renegade

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.util.UUID

/** Large native controls and selectable tags, protected by fresh server authentication. */
class ProfileActivity : ComponentActivity() {
    private val sessions by inject<SessionRepository>()
    private val servers by inject<ServerRepository>()
    private val users by inject<UserRepository>()
    private lateinit var column: LinearLayout
    private lateinit var api: ProfileApi
    private var working = false
    private var unlocked = false
    private var server = ""
    private var status: TextView? = null
    private val amber = Color.rgb(255, 175, 69)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        page("Profiles")
        if (users.currentUser.value?.policy?.isAdministrator != true) {
            label("An administrator must sign in to create profiles or change restrictions. Use the profile icon on Home to switch accounts.")
            action("Back") { finish() }
            return
        }
        label("Confirm your administrator password to manage profiles. Changes apply on your Jellyfin server and other clients.")
        val password = input("Administrator password", true)
        action("Unlock profile settings") {
            work {
                val session = checkNotNull(sessions.currentSession.value)
                server = checkNotNull(servers.getServer(session.serverId)).address
                val bootstrap = ProfileApi(server, session.accessToken)
                val result = JSONObject(bootstrap.request("Users/AuthenticateByName", JSONObject()
                    .put("Username", checkNotNull(users.currentUser.value).name).put("Pw", password.text.toString())))
                password.text.clear()
                val verified = result.getJSONObject("User")
                check(verified.getJSONObject("Policy").optBoolean("IsAdministrator") &&
                    verified.getString("Id").replace("-", "") == session.userId.toString().replace("-", "")) { "Administrator confirmation failed." }
                api = ProfileApi(server, result.getString("AccessToken"))
                unlocked = true
                showUsers()
            }
        }
        action("Cancel") { finish() }
    }

    private fun page(title: String) {
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(24), dp(40), dp(24))
            setBackgroundColor(Color.rgb(16, 17, 20))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(column) })
        label("RENEGADEFIN  /  $title", 24f, amber)
        status = label("")
    }
    private fun label(text: String, size: Float = 18f, colour: Int = Color.WHITE): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(colour); setPadding(0, dp(8), 0, dp(8)); column.addView(this)
    }
    private fun action(title: String, click: () -> Unit): Button = Button(this).apply {
        text = title; isAllCaps = false; textSize = 18f
        minHeight = dp(54)
        setTextColor(Color.WHITE)
        background = surface(Color.rgb(35, 38, 45))
        val layout = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        column.addView(this, layout)
        setOnFocusChangeListener { _, focused ->
            background = surface(if (focused) amber else Color.rgb(35, 38, 45))
            setTextColor(if (focused) Color.BLACK else Color.WHITE)
        }
        setOnClickListener { if (!working) click() }
    }
    private fun surface(colour: Int) = GradientDrawable().apply { setColor(colour); cornerRadius = dp(10).toFloat() }
    private fun input(hint: String, secret: Boolean = false) = EditText(this).apply {
        this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); textSize = 20f; setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL
        column.addView(this, LinearLayout.LayoutParams(-1, dp(58)))
    }
    private fun work(block: suspend () -> Unit) {
        if (working) return
        working = true
        status?.text = "Working…"
        lifecycleScope.launch {
            try { block(); status?.text = "" }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { status?.text = e.message ?: "Could not complete the request. Try again." }
            finally { working = false }
        }
    }
    private suspend fun showUsers() {
        check(unlocked)
        val profiles = JSONArray(api.request("Users")).objects()
        page("Manage profiles")
        label("Profiles use separate Jellyfin accounts, with their own history and favourites. Administrator accounts are protected from this editor.")
        action("Create profile") { createForm() }
        profiles.filter { !it.getJSONObject("Policy").optBoolean("IsAdministrator") }.forEach { profile ->
            action(profile.getString("Name")) { work { edit(profile.getString("Id")) } }
        }
        action("Done") { finish() }
    }
    private fun createForm() {
        page("Create profile")
        val name = input("Profile name")
        val password = input("Sign-in password", true)
        label("A new profile starts disabled with no library access. Set restrictions, then choose Enable profile. Its password is also used in other Jellyfin apps.")
        action("Create and set restrictions") {
            if (name.text.isBlank() || password.text.length < 8) { status?.text = "Enter a name and a password of at least 8 characters."; return@action }
            val chosenName = name.text.toString().trim()
            val chosenPassword = password.text.toString()
            work {
                // An unguessable temporary password protects the creation-to-policy interval.
                val created = JSONObject(api.request("Users/New", JSONObject().put("Name", chosenName).put("Password", UUID.randomUUID().toString())))
                val id = created.getString("Id")
                val policy = created.getJSONObject("Policy")
                policy.put("IsDisabled", true).put("IsAdministrator", false).put("EnableAllFolders", false).put("EnabledFolders", JSONArray())
                    .put("EnableContentDeletion", false).put("EnableRemoteControlOfOtherUsers", false)
                api.request("Users/$id/Policy", policy)
                api.request("Users/Password?userId=$id", JSONObject().put("NewPw", chosenPassword))
                password.text.clear()
                edit(id)
            }
        }
        action("Cancel") { work { showUsers() } }
    }
    private suspend fun edit(id: String) {
        val profile = api.user(id)
        check(!profile.getJSONObject("Policy").optBoolean("IsAdministrator")) { "Administrator accounts cannot be edited here." }
        val original = profile.getJSONObject("Policy")
        val edited = JSONObject(original.toString())
        val libraries = JSONArray(api.request("Library/VirtualFolders")).objects()
        val ratings = JSONArray(api.request("Localization/ParentalRatings")).objects()
        val filters = JSONObject(api.request("Items/Filters"))
        val tags = (filters.optJSONArray("Tags") ?: JSONArray()).strings().sortedWith(String.CASE_INSENSITIVE_ORDER)
        page(profile.getString("Name"))
        label("Choose restrictions, review them, then save. These are server-enforced account settings.")
        action("Choose profile avatar") { avatarPicker(id) }
        val enabled = CheckBox(this).apply { text = "Enable profile"; isChecked = !edited.optBoolean("IsDisabled"); setTextColor(Color.WHITE); textSize = 20f; column.addView(this) }
        val all = CheckBox(this).apply { text = "Allow every library (including future libraries)"; isChecked = edited.optBoolean("EnableAllFolders"); setTextColor(Color.WHITE); textSize = 20f; column.addView(this) }
        val selectedLibraries = (edited.optJSONArray("EnabledFolders") ?: JSONArray()).strings().toMutableSet()
        val libraryButton = action("Choose allowed libraries") {
            choose("Allowed libraries", libraries.map { it.optString("Name") to it.getString("ItemId") }, selectedLibraries) {}
        }
        all.setOnCheckedChangeListener { _, checked -> libraryButton.isEnabled = !checked }
        libraryButton.isEnabled = !all.isChecked
        var maxRating: Int? = if (edited.isNull("MaxParentalRating")) null else edited.getInt("MaxParentalRating")
        val ratingButton = action("Age rating: ${maxRating?.toString() ?: "Unrestricted"}") {}
        ratingButton.setOnClickListener {
            if (!working) {
                val choices = listOf("Unrestricted") + ratings.map { it.getString("Name") }
                AlertDialog.Builder(this).setTitle("Maximum age rating").setItems(choices.toTypedArray()) { _, which ->
                    maxRating = if (which == 0) null else ratings[which - 1].getInt("Value")
                    ratingButton.text = "Age rating: ${choices[which]}"
                }.setNegativeButton("Cancel", null).show()
            }
        }
        val unrated = (edited.optJSONArray("BlockUnratedItems") ?: JSONArray()).strings().toMutableSet()
        action("Choose unrated content to block") {
            choose("Block unrated content", listOf("Movie", "Series", "Trailer", "Music", "Book", "LiveTvChannel", "Other").map { it to it }, unrated) {}
        }
        val allowed = (edited.optJSONArray("AllowedTags") ?: JSONArray()).strings().toMutableSet()
        val blocked = (edited.optJSONArray("BlockedTags") ?: JSONArray()).strings().toMutableSet()
        val tagOptions = (tags + allowed + blocked).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).map { it to it }
        label("Allowed tags limit content to those tags. Blocked tags exclude content. Leave allowed tags empty for no tag allow-list.")
        action("Choose allowed tags (${allowed.size})") { choose("Allowed tags", tagOptions, allowed) { blocked.removeAll(allowed) } }
        action("Choose blocked tags (${blocked.size})") { choose("Blocked tags", tagOptions, blocked) { allowed.removeAll(blocked) } }
        action("Review and save restrictions") {
            edited.put("IsDisabled", !enabled.isChecked).put("EnableAllFolders", all.isChecked)
                .put("EnabledFolders", JSONArray(selectedLibraries.toList())).put("MaxParentalRating", maxRating ?: JSONObject.NULL)
                .put("AllowedTags", JSONArray(allowed.toList())).put("BlockedTags", JSONArray(blocked.toList()))
                .put("BlockUnratedItems", JSONArray(unrated.toList()))
            val summary = "Profile: ${profile.getString("Name")}\nEnabled: ${enabled.isChecked}\nLibraries: ${if (all.isChecked) "All" else selectedLibraries.size.toString()}\nMaximum rating: ${maxRating ?: "Unrestricted"}\nAllowed tags: ${allowed.joinToString().ifEmpty { "Any" }}\nBlocked tags: ${blocked.joinToString().ifEmpty { "None" }}\nUnrated blocked: ${unrated.joinToString().ifEmpty { "None" }}"
            AlertDialog.Builder(this).setTitle("Confirm profile restrictions").setMessage(summary)
                .setPositiveButton("Save") { _, _ -> work { api.savePolicy(id, original, edited); showUsers(); Toast.makeText(this, "Restrictions saved and verified", Toast.LENGTH_LONG).show() } }
                .setNegativeButton("Cancel", null).show()
        }
        action("Discard changes") { work { showUsers() } }
    }
    private fun choose(title: String, options: List<Pair<String, String>>, selected: MutableSet<String>, changed: () -> Unit) {
        if (options.isEmpty()) { AlertDialog.Builder(this).setMessage("No options are available on this server.").setPositiveButton("OK", null).show(); return }
        val draft = selected.toMutableSet()
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(options.map { it.first }.toTypedArray(), options.map { it.second in draft }.toBooleanArray()) { _, index, checked ->
                if (checked) draft.add(options[index].second) else draft.remove(options[index].second)
            }.setPositiveButton("Apply") { _, _ -> selected.clear(); selected.addAll(draft); changed() }
            .setNegativeButton("Cancel", null).show()
    }
    private fun avatarPicker(id: String) {
        val avatars = listOf("Blaze" to 0xFFFF8735.toInt(), "Tide" to 0xFF36BDE8.toInt(), "Forest" to 0xFF6CD49B.toInt(),
            "Violet" to 0xFFC699FF.toInt(), "Gold" to 0xFFFFD166.toInt(), "Ice" to 0xFFE2F4FF.toInt())
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val dialog = AlertDialog.Builder(this).setTitle("Choose an avatar").setView(list).setNegativeButton("Cancel", null).create()
        avatars.forEach { (name, colour) ->
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(16, 17, 20))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
            val fin = Path().apply {
                moveTo(137f, 18f); cubicTo(150f, 63f, 211f, 82f, 211f, 143f)
                cubicTo(211f, 190f, 173f, 223f, 124f, 231f); lineTo(149f, 191f)
                cubicTo(125f, 205f, 95f, 205f, 72f, 186f); cubicTo(47f, 165f, 42f, 132f, 58f, 105f)
                lineTo(99f, 57f); lineTo(89f, 113f); cubicTo(107f, 99f, 129f, 65f, 137f, 18f); close()
            }
            canvas.drawPath(fin, paint)
            paint.color = Color.rgb(16, 17, 20)
            canvas.drawPath(Path().apply { moveTo(116f, 108f); lineTo(172f, 143f); lineTo(101f, 172f); close() }, paint)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(ImageView(this).apply { setImageBitmap(bitmap) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            row.addView(Button(this).apply {
                text = name; isAllCaps = false
                setOnClickListener {
                    if (!working) AlertDialog.Builder(this@ProfileActivity).setTitle("Use $name?")
                        .setMessage("This changes the profile picture on your server.")
                        .setPositiveButton("Use avatar") { _, _ ->
                            dialog.dismiss()
                            work {
                                val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
                                api.uploadAvatar(id, bytes)
                                Toast.makeText(this@ProfileActivity, "Avatar saved", Toast.LENGTH_SHORT).show()
                            }
                        }.setNegativeButton("Cancel", null).show()
                }
            }, LinearLayout.LayoutParams(-1, dp(48)))
            list.addView(row)
        }
        dialog.show()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
