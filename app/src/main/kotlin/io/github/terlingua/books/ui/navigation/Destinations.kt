package io.github.terlingua.books.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object LibraryKey : NavKey
@Serializable data class BookKey(val slug: String, val sectionKey: String? = null) : NavKey
@Serializable data object SearchKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object AboutKey : NavKey
@Serializable data class QrShareKey(val slug: String) : NavKey
@Serializable data object QrScanKey : NavKey
