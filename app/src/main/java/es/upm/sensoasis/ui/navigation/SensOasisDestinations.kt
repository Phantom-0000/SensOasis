package es.upm.sensoasis.ui.navigation

/**
 * Navigation routes for the "one Activity, several screens" structure.
 */
object SensOasisDestinations {
    const val ARG_FOUNTAIN_ID = "fountainId"

    const val LIST_ROUTE = "list"
    const val DETAIL_ROUTE = "detail/{$ARG_FOUNTAIN_ID}"
    const val REPORT_ROUTE = "report/{$ARG_FOUNTAIN_ID}"

    fun detailRoute(fountainId: String): String = "detail/$fountainId"
    fun reportRoute(fountainId: String): String = "report/$fountainId"
}
