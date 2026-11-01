package com.rfandango.haku_x.ui.library.cover

import android.content.Context

/** How the library looks: the layout (Grid is the default) and whether the cards show each game's name and ID. */
class LibraryPrefs(context: Context) {
  private val prefs = context.getSharedPreferences("library_view", Context.MODE_PRIVATE)

  var grid: Boolean
    get() = prefs.getBoolean("grid", true)
    set(value) = prefs.edit().putBoolean("grid", value).apply()

  var showInfo: Boolean
    get() = prefs.getBoolean("show_info", false)
    set(value) = prefs.edit().putBoolean("show_info", value).apply()
}