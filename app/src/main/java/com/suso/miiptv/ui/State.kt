package com.suso.miiptv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Prefs

sealed interface Screen {
    data class Movie(val item: Item) : Screen
    data class Series(val item: Item) : Screen
    data object Settings : Screen
    data object HiddenCats : Screen
    data object MissingLogos : Screen
}

enum class Tab(val label: String) {
    HOME("Inicio"),
    LIVE("TV"),
    GUIDE("Guía"),
    MOVIES("Películas"),
    SERIES("Series"),
    SEARCH("Buscar"),
}

data class HomeRow(
    val key: String,
    val title: String,
    val kind: Kind?,
    val items: List<Item>,
    val progress: List<Prefs.Progress>? = null,
)

class BrowseState {
    var selected by mutableStateOf<String?>(null)
    var grid by mutableStateOf(LazyGridState())
    val cats = LazyListState()
    var focused by mutableStateOf<Item?>(null)
    var loadedSel: String? = null
    var lastItems: List<Item>? = null
    /** Última tarjeta de la cuadrícula que tuvo el foco (para volver a ella con ▶ desde las categorías). */
    var lastIdx = -1
    /** Tarjetas de la cuadrícula que están en pantalla, por posición. */
    val reqs = HashMap<Int, androidx.compose.ui.focus.FocusRequester>()
    /** Filas de categorías que están en pantalla. */
    val catReqs = HashMap<String, androidx.compose.ui.focus.FocusRequester>()
}

/** Estado de navegación que sobrevive al ir y volver de las fichas y del reproductor. */
object UiState {
    var tab by mutableStateOf(Tab.HOME)

    /** true mientras el cursor está en el menú de arriba (pestañas o ajustes). */
    var topBarFocused by mutableStateOf(false)
    val stack = mutableStateListOf<Screen>()
    private val focusStack = ArrayList<String?>()

    private val browseStates = HashMap<Kind, BrowseState>()
    fun browseState(kind: Kind): BrowseState = browseStates.getOrPut(kind) { BrowseState() }

    var homeList = LazyListState()
        private set
    private val rowStates = HashMap<String, LazyListState>()
    fun rowState(key: String): LazyListState = rowStates.getOrPut(key) { LazyListState() }

    var homeCache: List<HomeRow>? = null
    var homeVer = ""
    var searchQuery = ""

    private val tabRequesters = HashMap<Tab, FocusRequester>()
    fun tabRequester(t: Tab): FocusRequester = tabRequesters.getOrPut(t) { FocusRequester() }

    fun push(s: Screen) {
        focusStack.add(FocusMemory.last)
        stack.add(s)
    }

    fun pop() {
        if (stack.isEmpty()) return
        stack.removeAt(stack.lastIndex)
        FocusMemory.restore = if (focusStack.isEmpty()) null else focusStack.removeAt(focusStack.lastIndex)
    }

    fun resetAll() {
        stack.clear()
        focusStack.clear()
        browseStates.clear()
        rowStates.clear()
        homeList = LazyListState()
        homeCache = null
        homeVer = ""
        searchQuery = ""
        tab = Tab.HOME
        FocusMemory.restore = null
    }
}
