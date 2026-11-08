package com.karapp.drawguidance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.karapp.drawguidance.ui.components.MainSectionFrame
import com.karapp.drawguidance.ui.components.MainTab
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditScreen
import com.karapp.drawguidance.ui.screens.canvaslist.CanvasListScreen
import com.karapp.drawguidance.ui.screens.images.ImagesScreen
import com.karapp.drawguidance.ui.screens.settings.SettingsScreen

object Routes {
    /** v30 Ana sayfa (uygulamanın açılış ekranı). */
    const val HOME = "home"
    const val CANVAS_LIST = "canvasList"
    const val CANVAS_EDIT = "canvasEdit/{localId}"
    const val SETTINGS = "settings"
    const val IMAGES = "images"
    const val LOGS = "logs"
    /** v33 hesap ayarları. */
    const val ACCOUNT = "account"
    /** v39 abonelik (Free / Premium). */
    const val SUBSCRIPTION = "subscription"
    /** v23 3B Şekiller bölümü ve 3B düzenleyici penceresi. */
    const val MODELS = "models3d"
    const val MODEL_EDIT = "model3dEdit?modelId={modelId}&canvasId={canvasId}&strokeId={strokeId}&name={name}"
    fun canvasEdit(localId: String) = "canvasEdit/$localId"
    /** v33 [name]: yeni 3B şeklin adı (ana sayfadaki 3B tuval oluşturma). */
    fun modelEdit(modelId: String?, canvasId: String?, strokeId: String?, name: String? = null) =
        "model3dEdit?modelId=${modelId.orEmpty()}&canvasId=${canvasId.orEmpty()}&strokeId=${strokeId.orEmpty()}&name=${android.net.Uri.encode(name.orEmpty())}"
}

@Composable
fun DrawGuidanceNavGraph(
    navController: NavHostController = rememberNavController(),
    /** v30 açılış ekranı: Ana sayfa (testlerde Tuvallerim). */
    startOnHome: Boolean = true,
) {
    // Alt menüdeki bölümler arasında geçiş: her bölüm tek kopya, geri tuşu açılış ekranına döner
    fun openTab(tab: MainTab) {
        val route = when (tab) {
            MainTab.HOME -> Routes.HOME
            MainTab.CANVASES -> Routes.CANVAS_LIST
            MainTab.IMAGES -> Routes.IMAGES
            MainTab.MODELS -> Routes.MODELS
            MainTab.SETTINGS -> Routes.SETTINGS
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // v42 gezinme izi: hata ve beklenmedik kapanma raporlarında son ekran görünür
    androidx.compose.runtime.DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, _ ->
            com.karapp.drawguidance.diagnostics.AppErrors.breadcrumb("ekran: " + (dest.route?.substringBefore('?')?.substringBefore('/') ?: "?"))
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = if (startOnHome) Routes.HOME else Routes.CANVAS_LIST) {
        composable(Routes.HOME) {
            MainSectionFrame(MainTab.HOME, ::openTab) { bar ->
                com.karapp.drawguidance.ui.screens.home.HomeScreen(
                    onOpenCanvas = { localId -> navController.navigate(Routes.canvasEdit(localId)) },
                    onOpenTab = ::openTab,
                    onNewModel = { name -> navController.navigate(Routes.modelEdit(null, null, null, name)) },
                    bottomBar = bar,
                    // v34 profil fotoğrafına dokununca doğrudan hesap ayarları
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                )
            }
        }
        composable(Routes.CANVAS_LIST) {
            MainSectionFrame(MainTab.CANVASES, ::openTab) { bar ->
                CanvasListScreen(
                    onOpenCanvas = { localId -> navController.navigate(Routes.canvasEdit(localId)) },
                    bottomBar = bar,
                )
            }
        }
        composable(Routes.IMAGES) {
            // v44 çocuk kilidinde Kaydedilenler, 3B Şekiller ve abonelik açılmaz
            if (!com.karapp.drawguidance.ui.kids.kidsBlocked { openTab(MainTab.HOME) }) MainSectionFrame(MainTab.IMAGES, ::openTab) { bar -> ImagesScreen(bottomBar = bar) }
        }
        composable(Routes.MODELS) {
            if (!com.karapp.drawguidance.ui.kids.kidsBlocked { openTab(MainTab.HOME) }) MainSectionFrame(MainTab.MODELS, ::openTab) { bar ->
                com.karapp.drawguidance.ui.screens.model3d.Model3DListScreen(
                    onOpen = { id -> navController.navigate(Routes.modelEdit(id, null, null)) },
                    bottomBar = bar,
                )
            }
        }
        composable(
            Routes.MODEL_EDIT,
            arguments = listOf(
                androidx.navigation.navArgument("modelId") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("canvasId") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("strokeId") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            fun arg(name: String) = entry.arguments?.getString(name)?.ifEmpty { null }
            if (com.karapp.drawguidance.ui.kids.kidsBlocked { navController.popBackStack() }) return@composable
            // v34 çizim alanı koordinatları soldan sağa: Arapçada da düzenleyici soldan sağa dizilir (yazılar sağdan sola)
            EditorDirection {
                com.karapp.drawguidance.ui.premium.PremiumScope(null, { navController.navigate(Routes.SUBSCRIPTION) }) {
                com.karapp.drawguidance.ui.screens.model3d.Model3DEditorScreen(
                    modelId = arg("modelId"),
                    canvasId = arg("canvasId"),
                    strokeId = arg("strokeId"),
                    initialName = arg("name"),
                    onBack = { navController.popBackStack() },
                )
                }
            }
        }
        composable(Routes.SETTINGS) {
            MainSectionFrame(MainTab.SETTINGS, ::openTab) { bar ->
                SettingsScreen(bottomBar = bar, onOpenAccount = { navController.navigate(Routes.ACCOUNT) }, onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) })
            }
        }
        composable(Routes.ACCOUNT) {
            com.karapp.drawguidance.ui.screens.account.AccountSettingsScreen(
                onBack = { navController.popBackStack() },
                vm = org.koin.androidx.compose.koinViewModel(),
            )
        }
        composable(Routes.SUBSCRIPTION) {
            if (!com.karapp.drawguidance.ui.kids.kidsBlocked { navController.popBackStack() }) com.karapp.drawguidance.ui.screens.subscription.SubscriptionScreen(
                onBack = { navController.popBackStack() },
                vm = org.koin.androidx.compose.koinViewModel(),
            )
        }
        composable(Routes.LOGS) {
            com.karapp.drawguidance.ui.screens.logs.LogsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CANVAS_EDIT) { backStackEntry ->
            val localId = backStackEntry.arguments?.getString("localId") ?: return@composable
            EditorDirection {
                // v39 Premium kilitleri ve tuval kapatılınca reklam
                com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditRoute(
                    canvasLocalId = localId,
                    onBack = { navController.popBackStack() },
                    onOpenModel3d = { modelId, strokeId -> navController.navigate(Routes.modelEdit(modelId, localId, strokeId)) },
                    onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                )
            }
        }
    }
}

/**
 * v34 düzenleyiciler (tuval, 3B) her dilde soldan sağa yerleşir: çizim alanındaki tutamak, balon ve araç konumları
 * tuval koordinatlarıyla hesaplanır. Yazılar Arapçada yine sağdan sola yazılır.
 */
@androidx.compose.runtime.Composable
private fun EditorDirection(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
        content = content,
    )
}
