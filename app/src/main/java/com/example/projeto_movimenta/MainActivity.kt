package com.example.projeto_movimenta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.projeto_movimenta.ui.theme.Layout
import com.example.projeto_movimenta.ui.theme.DeepPetrol
import com.example.projeto_movimenta.ui.theme.Projeto_MovimentaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val controller by viewModels<InventoryController> {
        viewModelFactory { initializer { InventoryController(InventoryRepository(application)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { Projeto_MovimentaTheme { InventoryApp(controller) } }
    }
}

private enum class Screen(val title: String, val tab: String? = null, val icon: Int = 0) {
    STOCK("Estoque", "Início", R.drawable.ic_home),
    REGISTER("Cadastrar produto", "Novo produto", R.drawable.ic_add_product),
    NEW_LOT("Adicionar lote", "Novo lote", R.drawable.ic_add_lot),
    DETAIL("Detalhes do produto"),
    EDIT_PRODUCT("Editar produto"),
    EDIT_LOT("Editar lote"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InventoryApp(
    controller: InventoryController,
    barcodeReader: @Composable ((String) -> Unit, () -> Unit) -> Unit = { result, close ->
        BarcodeScannerDialog(result, close)
    },
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    var screen by rememberSaveable { mutableStateOf(Screen.STOCK) }
    var fromDetail by rememberSaveable { mutableStateOf(false) }
    var productId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lotId by rememberSaveable { mutableStateOf<Long?>(null) }
    var initialBarcode by rememberSaveable { mutableStateOf("") }
    val outcome = controller.outcome
    val busy = controller.busy || outcome != null

    fun open(destination: Screen, product: Long? = null, lot: Long? = null, barcode: String = "") {
        if (destination == Screen.DETAIL) screenStates.removeState(Screen.DETAIL.name)
        fromDetail = screen == Screen.DETAIL && destination != Screen.STOCK
        screen = destination
        productId = product
        lotId = lot
        initialBarcode = barcode
    }

    fun back() {
        if (fromDetail && screen != Screen.DETAIL) {
            screen = Screen.DETAIL
            fromDetail = false
            lotId = null
        } else {
            open(Screen.STOCK)
        }
    }

    fun acknowledge(result: InventoryOutcome) {
        controller.consume(result)
        result.message?.let { scope.launch { snackbar.showSnackbar(it) } }
    }

    val data = controller.inventory
    LaunchedEffect(outcome) {
        if (outcome != null) {
            when (outcome.effect) {
                InventoryEffect.Return -> { back(); acknowledge(outcome) }
                null -> acknowledge(outcome)
                is InventoryEffect.ClearAdjustment, is InventoryEffect.SelectSector -> Unit
            }
        }
    }
    BackHandler(screen != Screen.STOCK) { if (!busy) back() }

    if (controller.loadFailed || data == null) {
        Scaffold(
            topBar = { StatusBarBackdrop() },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                PageBackdrop(Modifier.matchParentSize())
                StartupContent(controller.loadFailed, controller.busy, controller::reload)
            }
        }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatusBarBackdrop()
                TopAppBar(
                    modifier = Modifier.widthIn(max = Layout.contentWidth),
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Layout.gap)) {
                            if (screen == Screen.STOCK || screen == Screen.REGISTER) {
                                AppIcon(R.drawable.ic_mv_boxes, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(screen.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        if (screen.tab == null || screen == Screen.NEW_LOT) {
                            IconButton(onClick = ::back, enabled = !busy) {
                                Icon(painterResource(R.drawable.ic_arrow_back), "Voltar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (screen.tab != null && !fromDetail) {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Screen.entries.filter { it.tab != null }.forEach { destination ->
                        NavigationBarItem(
                            selected = screen == destination,
                            onClick = { if (screen != destination) open(destination) },
                            enabled = !busy,
                            icon = { AppIcon(destination.icon) },
                            label = { Text(requireNotNull(destination.tab), style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PageBackdrop(Modifier.matchParentSize())
            val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                .widthIn(max = Layout.contentWidth).fillMaxWidth().fillMaxHeight()
            when (screen) {
                Screen.STOCK -> screenStates.SaveableStateProvider(Screen.STOCK.name) {
                    StockScreen(
                        inventory = data,
                        repository = controller.repository,
                        modifier = modifier,
                        barcodeReader = barcodeReader,
                        onUnknownBarcode = { open(Screen.REGISTER, barcode = it) },
                        onAdjust = { open(Screen.DETAIL, it) },
                        onEdit = { open(Screen.EDIT_LOT, it.productId, it.id) },
                    )
                }
                Screen.DETAIL -> {
                    val product = data.products.find { it.id == productId }
                    if (product != null) {
                        screenStates.SaveableStateProvider(Screen.DETAIL.name) {
                            ProductManagementScreen(
                                product = product,
                                lots = data.lots.filter { it.productId == product.id },
                                sectors = data.sectors,
                                repository = controller.repository,
                                busy = busy,
                                modifier = modifier,
                                adjustedLotId = (outcome?.effect as? InventoryEffect.ClearAdjustment)?.lotId,
                                onAdjustmentAcknowledged = { outcome?.let(::acknowledge) },
                                onAdjust = { id, amount, boxes, direction ->
                                    controller.write {
                                        if (adjust(id, amount, boxes, direction)) {
                                            InventoryOutcome("Saldo atualizado.", InventoryEffect.ClearAdjustment(id))
                                        } else {
                                            InventoryOutcome("O saldo atual não permite esse ajuste.")
                                        }
                                    }
                                },
                                onNewLot = { open(Screen.NEW_LOT, product.id) },
                                onEdit = { open(Screen.EDIT_LOT, product.id, it.id) },
                                onEditProduct = { open(Screen.EDIT_PRODUCT, product.id) },
                                onRemoveProduct = {
                                    controller.write {
                                        removeProduct(product.id)
                                        InventoryOutcome("Produto excluído.", InventoryEffect.Return)
                                    }
                                },
                            )
                        }
                    } else if (outcome?.effect != InventoryEffect.Return) {
                        MissingItem(modifier, ::back)
                    }
                }
                Screen.REGISTER, Screen.NEW_LOT, Screen.EDIT_PRODUCT, Screen.EDIT_LOT -> {
                    val mode = when (screen) {
                        Screen.REGISTER -> ProductFormMode.NEW_PRODUCT
                        Screen.NEW_LOT -> ProductFormMode.NEW_LOT
                        Screen.EDIT_PRODUCT -> ProductFormMode.EDIT_PRODUCT
                        else -> ProductFormMode.EDIT_LOT
                    }
                    val product = data.products.find { it.id == productId }
                    val lot = data.lots.find { it.id == lotId }
                    val missing = (productId != null && product == null) ||
                        (mode == ProductFormMode.EDIT_LOT && lot == null)
                    if (missing && outcome?.effect != InventoryEffect.Return) {
                        MissingItem(modifier, ::back)
                    } else if (outcome?.effect != InventoryEffect.Return) {
                        key(screen, productId, lotId, initialBarcode) {
                            ProductForm(
                                mode = mode,
                                inventory = data,
                                initialBarcode = initialBarcode,
                                initialProduct = product,
                                existingLot = lot,
                                repository = controller.repository,
                                busy = busy,
                                modifier = modifier,
                                createdSectorId = (outcome?.effect as? InventoryEffect.SelectSector)?.id,
                                onSectorAcknowledged = { outcome?.let(::acknowledge) },
                                onCreateSector = { name ->
                                    controller.write { InventoryOutcome(effect = InventoryEffect.SelectSector(createSector(name).id)) }
                                },
                                onExistingProduct = { open(Screen.NEW_LOT, it) },
                                onSave = { item, batch, photo ->
                                    controller.write {
                                        val message = when (mode) {
                                            ProductFormMode.EDIT_PRODUCT -> {
                                                editProduct(item, photo)
                                                "Produto atualizado."
                                            }
                                            ProductFormMode.EDIT_LOT -> {
                                                editLot(requireNotNull(batch))
                                                "Lote atualizado."
                                            }
                                            ProductFormMode.NEW_PRODUCT, ProductFormMode.NEW_LOT -> {
                                                register(item, requireNotNull(batch), photo)
                                                if (mode == ProductFormMode.NEW_PRODUCT) "Produto cadastrado." else "Lote cadastrado."
                                            }
                                        }
                                        InventoryOutcome(message, InventoryEffect.Return)
                                    }
                                },
                                onRemove = lot?.let { batch ->
                                    {
                                        controller.write {
                                            removeLot(batch.id)
                                            InventoryOutcome("Lote excluído.", InventoryEffect.Return)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBarBackdrop() {
    Spacer(Modifier.fillMaxWidth().background(DeepPetrol).windowInsetsTopHeight(WindowInsets.statusBars))
}

@Composable
private fun MissingItem(modifier: Modifier, onBack: () -> Unit) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Este item não está mais no estoque.")
        Button(onClick = onBack) { Text("Voltar") }
    }
}
