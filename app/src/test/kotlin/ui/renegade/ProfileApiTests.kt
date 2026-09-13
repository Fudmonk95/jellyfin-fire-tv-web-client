package org.jellyfin.androidtv.ui.renegade

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject

class ProfileApiTests : StringSpec({
    "restriction updates preserve unrelated and concurrently changed server permissions" {
        val original = policy()
        val fresh = policy().put("EnableContentDeletion", false).put("EnableAudioPlayback", false)
        val edited = JSONObject(original.toString()).put("BlockedTags", JSONArray(listOf("Adults")))
        var posted: JSONObject? = null
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    request.getHeader("Authorization")!!.contains("test-token") shouldBe true
                    return if (request.method == "POST") {
                        request.path shouldBe "/jellyfin/Users/child/Policy"
                        posted = JSONObject(request.body.readUtf8())
                        MockResponse().setResponseCode(204)
                    } else MockResponse().setBody(JSONObject().put("Policy", posted ?: fresh).toString())
                }
            }
            server.start()
            ProfileApi(server.url("/jellyfin").toString(), "test-token").savePolicy("child", original, edited)
            posted!!.getBoolean("EnableContentDeletion") shouldBe false
            posted!!.getBoolean("EnableAudioPlayback") shouldBe false
            posted!!.getJSONArray("BlockedTags").getString(0) shouldBe "Adults"
            server.requestCount shouldBe 3
        }
    }
    "a concurrent restriction change rejects the save before any POST" {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(JSONObject().put("Policy", policy().put("MaxParentalRating", 12)).toString()))
            server.start()
            runCatching { ProfileApi(server.url("/").toString(), "test-token").savePolicy("child", policy(), policy()) }.isFailure shouldBe true
            server.requestCount shouldBe 1
            server.takeRequest().method shouldBe "GET"
        }
    }
    "administrator promotion after opening the editor prevents policy writes" {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(JSONObject().put("Policy", policy().put("IsAdministrator", true)).toString()))
            server.start()
            runCatching { ProfileApi(server.url("/").toString(), "test-token").savePolicy("child", policy(), policy()) }.isFailure shouldBe true
            server.requestCount shouldBe 1
            server.takeRequest().method shouldBe "GET"
        }
    }
    "redirects are refused so credentials cannot be forwarded to another server" {
        MockWebServer().use { destination ->
            destination.start()
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destination.url("/")))
                server.start()
                runCatching { ProfileApi(server.url("/").toString(), "test-token").request("Users") }.isFailure shouldBe true
                destination.requestCount shouldBe 0
            }
        }
    }
})

private fun policy() = JSONObject()
    .put("IsAdministrator", false).put("EnableContentDeletion", true)
    .put("EnableAllFolders", false).put("EnabledFolders", JSONArray())
    .put("MaxParentalRating", JSONObject.NULL).put("BlockedTags", JSONArray())
    .put("AllowedTags", JSONArray()).put("BlockUnratedItems", JSONArray()).put("IsDisabled", false)
