package org.jellyfin.androidtv.ui.renegade

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Uses the server's user policies; restrictions are never only client-side filters. */
class ProfileApi(private val server: String, private val token: String) {
    suspend fun request(path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val connection = URL(server.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "MediaBrowser Client=\"RenegadeFin\", Device=\"TV\", DeviceId=\"renegadefin-profile-management\", Version=\"0.2.0\", Token=\"$token\"")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status in 200..299) {
                when (status) {
                    401, 403 -> "The server refused this action. Sign in again with an authorised administrator."
                    409 -> "This profile changed on the server. Reopen it before saving."
                    else -> "Server request failed ($status). No success has been confirmed."
                }
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    suspend fun user(id: String) = JSONObject(request("Users/$id"))
    suspend fun savePolicy(id: String, original: JSONObject, edited: JSONObject) {
        val fresh = user(id).getJSONObject("Policy")
        // Preserve unrelated permissions and reject concurrent edits to the fields we change.
        for (key in fields) {
            check(fresh.opt(key).toString() == original.opt(key).toString()) { "Restrictions changed elsewhere. Reopen this profile before saving." }
            if (edited.has(key)) fresh.put(key, edited.get(key))
        }
        request("Users/$id/Policy", fresh)
        val confirmed = user(id).getJSONObject("Policy")
        for (key in fields) check(confirmed.opt(key).toString() == edited.opt(key).toString()) {
            "The server did not confirm every restriction. Reopen the profile to check."
        }
    }
    companion object {
        val fields = listOf("EnableAllFolders", "EnabledFolders", "MaxParentalRating", "BlockedTags", "AllowedTags", "BlockUnratedItems", "IsDisabled")
    }
}

fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
