package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.ui.renegade.ProfileActivity
import org.jellyfin.androidtv.util.apiclient.*
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.*
import org.koin.android.ext.android.inject

/** Native, bounded home shelves. No WebView, injected CSS, or DOM focus scans. */
class HomeFragment : Fragment() {
    private val api by inject<ApiClient>()
    private val users by inject<UserRepository>()
    private val navigation by inject<NavigationRepository>()
    private data class Shelf(val title: String, val items: List<BaseItemDto>, val library: Boolean = false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
        val user by users.currentUser.collectAsState()
        var shelves by remember(user?.id) { mutableStateOf<List<Shelf>>(emptyList()) }
        var error by remember(user?.id) { mutableStateOf<String?>(null) }
        var loading by remember(user?.id) { mutableStateOf(true) }
        var refresh by remember { mutableIntStateOf(0) }
        var featured by remember(user?.id) { mutableStateOf<BaseItemDto?>(null) }
        var focusedItem by remember(user?.id) { mutableStateOf<BaseItemDto?>(null) }
        LaunchedEffect(focusedItem?.id) { delay(180); focusedItem?.let { featured = it } }
        val startFocus = remember { FocusRequester() }
        LaunchedEffect(user?.id, refresh) {
            loading = true
            error = null
            try {
                val result = coroutineScope {
                    val libraries = async { api.userViewsApi.getUserViews().content.items }
                    val resume = async { api.itemsApi.getResumeItems(limit = 20, fields = ItemRepository.browseFields).content.items }
                    val next = async { api.tvShowsApi.getNextUp(limit = 20, fields = ItemRepository.browseFields).content.items }
                    val latest = async { api.itemsApi.getItems(
                        recursive = true, includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        sortBy = setOf(ItemSortBy.DATE_CREATED), sortOrder = setOf(SortOrder.DESCENDING),
                        fields = ItemRepository.browseFields, limit = 30,
                    ).content.items }
                    listOf(Shelf("Continue watching", resume.await()), Shelf("Next up", next.await()), Shelf("Your libraries", libraries.await(), true), Shelf("Recently added", latest.await()))
                }
                shelves = result.filter { it.items.isNotEmpty() }
                featured = result.firstOrNull { !it.library && it.items.isNotEmpty() }?.items?.firstOrNull()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "Couldn't load your library. Check the server connection and try again." }
            finally { loading = false }
        }
        LaunchedEffect(Unit) { startFocus.requestFocus() }
        JellyfinTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFF101114)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                MainToolbar(MainToolbarActiveButton.Home)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RENEGADEFIN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFAF45), letterSpacing = 3.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { startActivity(Intent(requireContext(), ProfileActivity::class.java)) }, modifier = Modifier.focusRequester(startFocus)) { Text("Manage profiles") }
                        Button(onClick = { refresh++ }) { Text("Refresh") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                featured?.let { item ->
                    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1D2026))) {
                        AsyncImage(model = item.itemBackdropImages.firstOrNull()?.getUrl(api, maxWidth = 1280), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF101114), Color(0x44101114)))))
                        Column(Modifier.padding(22.dp).fillMaxWidth(0.65f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.name.orEmpty(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(item.productionYear?.toString(), item.officialRating).joinToString("  •  "), color = Color(0xFFFFAF45))
                            Text(item.overview.orEmpty().replace(Regex("<[^>]*>"), ""), color = Color(0xFFE1E1E4), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Button(onClick = { navigation.navigate(Destinations.itemDetails(item.id)) }) { Text("View details / Play") }
                        }
                    }
                }
                if (loading) Text("Loading your library…", Modifier.padding(16.dp), color = Color.White)
                error?.let { Text(it, Modifier.padding(16.dp), color = Color(0xFFFFAF45)) }
                if (!loading && error == null && shelves.isEmpty()) Text("No media is available for this profile.", color = Color.White)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                    items(shelves, key = { it.title }) { shelf ->
                        Column {
                            Text(shelf.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            LazyRow(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(10.dp)) {
                                items(shelf.items, key = { it.id.toString() }) { item ->
                                    var focused by remember { mutableStateOf(false) }
                                    Column(Modifier.width(if (shelf.library) 185.dp else 132.dp)
                                        .graphicsLayer { scaleX = if (focused) 1.04f else 1f; scaleY = scaleX }
                                        .onFocusChanged { focused = it.isFocused; if (focused && !shelf.library) focusedItem = item }
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (focused) Color(0xFFFFAF45) else Color(0xFF20232A))
                                        .clickable {
                                            navigation.navigate(if (shelf.library) Destinations.libraryBrowser(item) else Destinations.itemDetails(item.id))
                                        }) {
                                        AsyncImage(model = item.itemImages[ImageType.PRIMARY]?.getUrl(api, maxWidth = 360),
                                            contentDescription = null, contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(if (shelf.library) 94.dp else 160.dp))
                                        Text(item.name.orEmpty(), Modifier.padding(8.dp), color = if (focused) Color(0xFF101114) else Color.White,
                                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
