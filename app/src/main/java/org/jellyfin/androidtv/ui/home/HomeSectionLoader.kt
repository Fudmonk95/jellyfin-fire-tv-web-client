package org.jellyfin.androidtv.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException

/** Each completed row can render immediately. One endpoint failure cannot cancel other rows. */
internal suspend fun <T> loadHomeSections(
    requests: Map<String, suspend () -> T>,
    onResult: (String, Result<T>) -> Unit,
) = coroutineScope {
    requests.forEach { (name, request) ->
        launch {
            val result = try {
                Result.success(request())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            onResult(name, result)
        }
    }
}

/** Fixed messages only: server responses can contain credentials or private content. */
internal fun homeFailureReason(failure: Throwable): String = when (failure) {
    is InvalidStatusException -> when (failure.status) {
        401 -> "Sign in again (HTTP 401)"
        403 -> "Access denied (HTTP 403); check account permissions"
        404 -> "Endpoint unavailable (HTTP 404)"
        else -> "Server returned HTTP ${failure.status}; try Refresh"
    }
    is InvalidContentException -> "Server response could not be read; client compatibility needs checking"
    is SecureConnectionException -> "Secure connection failed; check server certificate"
    is TimeoutException -> "Server timed out; try Refresh"
    else -> "Request failed; try Refresh and check the app log"
}
