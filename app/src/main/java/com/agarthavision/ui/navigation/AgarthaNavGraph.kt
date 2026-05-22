sealed class Screen(val route: String) {
    data object Capture    : Screen("capture")
    data object Queue      : Screen("queue")
    data object Validate   : Screen("validate/{sampleId}") {
        fun createRoute(sampleId: String) = "validate/$sampleId"
    }
    data object Reports    : Screen("reports")
    data object Admin      : Screen("admin")
    data object Settings   : Screen("settings")
}