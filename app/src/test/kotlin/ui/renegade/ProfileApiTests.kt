package org.jellyfin.androidtv.ui.renegade

import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject

class ProfileApiTests {
    @Test
    fun `restriction updates preserve unrelated and concurrently changed server permissions`() = runBlocking {
        val original = policy()
        val fresh = policy().put("EnableContentDeletion", false).put("EnableAudioPlayback", false)
        val edited = JSONObject(original.toString()).put("BlockedTags", JSONArray(listOf("Adults")))
        var posted: JSONObject? = null
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertEquals(true, request.getHeader("Authorization")!!.contains("test-token"))
                    return if (request.method == "POST") {
                        assertEquals("/jellyfin/Users/child/Policy", request.path)
                        posted = JSONObject(request.body.readUtf8())
                        MockResponse().setResponseCode(204)
                    } else MockResponse().setBody(JSONObject().put("Policy", posted ?: fresh).toString())
                }
            }
            server.start()
            ProfileApi(server.url("/jellyfin").toString(), "test-token").savePolicy("child", original, edited)
            assertEquals(false, posted!!.getBoolean("EnableContentDeletion"))
            assertEquals(false, posted!!.getBoolean("EnableAudioPlayback"))
            assertEquals("Adults", posted!!.getJSONArray("BlockedTags").getString(0))
            assertEquals(3, server.requestCount)
        }
    }
    @Test
    fun `a concurrent restriction change rejects the save before any POST`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(JSONObject().put("Policy", policy().put("MaxParentalRating", 12)).toString()))
            server.start()
            assertEquals(true, runCatching { ProfileApi(server.url("/").toString(), "test-token").savePolicy("child", policy(), policy()) }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        }
    }
    @Test
    fun `administrator promotion after opening the editor prevents policy writes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(JSONObject().put("Policy", policy().put("IsAdministrator", true)).toString()))
            server.start()
            assertEquals(true, runCatching { ProfileApi(server.url("/").toString(), "test-token").savePolicy("child", policy(), policy()) }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        }
    }
    @Test
    fun `redirects are refused so credentials cannot be forwarded to another server`() = runBlocking {
        MockWebServer().use { destination ->
            destination.start()
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destination.url("/")))
                server.start()
                assertEquals(true, runCatching { ProfileApi(server.url("/").toString(), "test-token").request("Users") }.isFailure)
                assertEquals(0, destination.requestCount)
            }
        }
    }
}

private fun policy() = JSONObject()
    .put("IsAdministrator", false).put("EnableContentDeletion", true)
    .put("EnableAllFolders", false).put("EnabledFolders", JSONArray())
    .put("MaxParentalRating", JSONObject.NULL).put("BlockedTags", JSONArray())
    .put("AllowedTags", JSONArray()).put("BlockUnratedItems", JSONArray()).put("IsDisabled", false)
