package com.libremobile.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Writer : Screen("writer?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri != null) "writer?uri=${URLEncoder.encode(uri, "UTF-8")}" else "writer"
        }
    }
    data object Calc : Screen("calc?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri != null) "calc?uri=${URLEncoder.encode(uri, "UTF-8")}" else "calc"
        }
    }
    data object Impress : Screen("impress?uri={uri}") {
        fun createRoute(uri: String? = null): String {
            return if (uri != null) "impress?uri=${URLEncoder.encode(uri, "UTF-8")}" else "impress"
        }
    }
}
