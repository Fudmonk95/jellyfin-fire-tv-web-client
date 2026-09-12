package org.jellyfin.androidtv.ui.renegade

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress

class ProfileApiTests : StringSpec({
    "restriction updates preserve unrelated and concurrently changed server permissions" {
        val original = policy()
        val fresh = policy().put("EnableContentDeletion", false).put("EnableAudioPlayback", false)
        val edited = JSONObject(original.toString()).put("BlockedTags", JSONArray(listOf("Adults")))
        var posted: JSONObject? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/jellyfin/Users/child") { exchange ->
            val result = if (exchange.requestMethod == "POST") {
                posted = JSONObject(exchange.requestBody.bufferedReader().readText())
                ""
            } else JSONObject().put("Policy", posted ?: fresh).toString()
            exchange.sendResponseHeaders(if (result.isEmpty()) 204 else 200, if (result.isEmpty()) -1 else result.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(result.toByteArray()) }
        }
        server.start()
        try {
            ProfileApi("http://127.0.0.1:${server.address.port}/jellyfin", "test-token").savePolicy("child", original, edited)
            posted!!.getBoolean("EnableContentDeletion") shouldBe false
            posted!!.getBoolean("EnableAudioPlayback") shouldBe false
            posted!!.getJSONArray("BlockedTags").getString(0) shouldBe "Adults"
        } finally { server.stop(0) }
    }
    "a concurrent restriction change rejects the save before any POST" {
        val original = policy()
        var writes = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/Users/child") { exchange ->
            if (exchange.requestMethod == "POST") writes++
            val bytes = JSONObject().put("Policy", policy().put("MaxParentalRating", 12)).toString().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = runCatching { ProfileApi("http://127.0.0.1:${server.address.port}", "test-token").savePolicy("child", original, original) }
            result.isFailure shouldBe true
            writes shouldBe 0
        } finally { server.stop(0) }
    }
    "administrator promotion after opening the editor prevents policy writes" {
        var writes = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/Users/child") { exchange ->
            if (exchange.requestMethod == "POST") writes++
            val bytes = JSONObject().put("Policy", policy().put("IsAdministrator", true)).toString().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            runCatching { ProfileApi("http://127.0.0.1:${server.address.port}", "test-token").savePolicy("child", policy(), policy()) }.isFailure shouldBe true
            writes shouldBe 0
        } finally { server.stop(0) }
    }
    "redirects are refused so credentials cannot be forwarded to another server" {
        var redirected = 0
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        destination.createContext("/") { exchange -> redirected++; exchange.sendResponseHeaders(204, -1); exchange.close() }
        destination.start()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${destination.address.port}/")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.start()
        try {
            runCatching { ProfileApi("http://127.0.0.1:${server.address.port}", "test-token").request("Users") }.isFailure shouldBe true
            redirected shouldBe 0
        } finally { server.stop(0); destination.stop(0) }
    }
})

private fun policy() = JSONObject()
    .put("IsAdministrator", false).put("EnableContentDeletion", true)
    .put("EnableAllFolders", false).put("EnabledFolders", JSONArray())
    .put("MaxParentalRating", JSONObject.NULL).put("BlockedTags", JSONArray())
    .put("AllowedTags", JSONArray()).put("BlockUnratedItems", JSONArray()).put("IsDisabled", false)
