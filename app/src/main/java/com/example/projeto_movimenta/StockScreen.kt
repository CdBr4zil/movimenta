package com.example.projeto_movimenta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.projeto_movimenta.ui.theme.Layout
import kotlinx.coroutines.delay

private enum class StockList(val label: String, val icon: Int) {
    ALL("Produtos", R.drawable.ic_mv_box),
    BUY("Comprar", R.drawable.ic_mv_cart),
    NEAR("Perto da validade", R.drawable.ic_mv_calendar),
    EXPIRED("Vencidos", R.drawable.ic_mv_alert),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StockScreen(
    inventory: Inventory,
    repository: InventoryRepository,
    modifier: Modifier,
    barcodeReader: @Composable ((String) -> Unit, () -> Unit) -> Unit,
    onUnknownBarcode: (String) -> Unit,
    onAdjust: (Long) -> Unit,
    onEdit: (StockLot) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var sectorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var list by rememberSaveable { mutableStateOf(StockList.ALL) }
    var scannerOpen by remember { mutableStateOf(false) }
    var missingCode by rememberSaveable { mutableStateOf<String?>(null) }
    var scanFeedback by remember { mutableStateOf<String?>(null) }
    val openScanner = rememberCameraAccess(
        onGranted = { scannerOpen = true },
        onDenied = { scanFeedback = "Permissão da câmera negada. Você pode digitar o código." },
    )
    val today by produceState(initialValue = StockRules.today()) {
        while (true) {
            value = StockRules.today()
            delay(30_000)
        }
    }
    val summary = remember(inventory, today) { inventory.summarize(today) }
    val lotsByProduct = remember(inventory.lots) { inventory.lots.groupBy { it.productId } }
    val productsById = remember(inventory.products) { inventory.products.associateBy { it.id } }
    val sectorsById = remember(inventory.sectors) { inventory.sectors.associateBy { it.id } }
    val query = search.trim()
    fun matches(product: Product): Boolean =
        query.isEmpty() || product.name.contains(query, ignoreCase = true) ||
            product.barcode?.contains(query) == true || product.brand?.contains(query, ignoreCase = true) == true

    if (scannerOpen) {
        barcodeReader({ code ->
            scannerOpen = false
            val product = inventory.findByBarcode(code)
            if (product != null) onAdjust(product.id) else missingCode = code
        }, { scannerOpen = false })
    }
    missingCode?.let { code ->
        AlertDialog(
            onDismissRequest = { missingCode = null },
            title = { Text("Produto fora do estoque") },
            text = { Text("O código $code ainda não está cadastrado.") },
            confirmButton = {
                TextButton(onClick = { missingCode = null; onUnknownBarcode(code) }) { Text("Cadastrar produto") }
            },
            dismissButton = { TextButton(onClick = { missingCode = null }) { Text("Cancelar") } },
        )
    }

    LazyColumn(
        modifier.imePadding(),
        contentPadding = PaddingValues(horizontal = Layout.gutter, vertical = Layout.compactGap),
        verticalArrangement = Arrangement.spacedBy(Layout.gap),
    ) {
        item {
            ActionButton("Escanear código de barras", { scanFeedback = null; openScanner() },
                R.drawable.ic_mv_scan, Modifier.fillMaxWidth().heightIn(min = 56.dp))
            scanFeedback?.let { Text(it) }
        }
        item {
            ProductField("Buscar por nome, marca ou código", search, { search = it }, icon = R.drawable.ic_mv_search)
        }
        item {
            val selected = inventory.sectors.find { it.id == sectorId }
            OptionPicker(
                label = "Setor",
                value = selected?.name ?: "Todos os setores",
                options = listOf(null to "Todos os setores") + inventory.sectors.map { it.id to it.name },
                icon = R.drawable.ic_mv_layers,
                onSelect = { sectorId = it },
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Layout.compactGap),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StockList.entries.forEach { choice ->
                    FilterChip(
                        selected = list == choice, onClick = { list = choice }, label = { Text(choice.label) },
                        leadingIcon = { AppIcon(choice.icon, tint = if (list == choice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.heightIn(min = Layout.target),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        when (list) {
            StockList.ALL, StockList.BUY -> {
                val isBuy = list == StockList.BUY
                val rows = (if (isBuy) summary.toBuy else summary.products).filter { row ->
                    val visibleLots = lotsByProduct[row.product.id].orEmpty()
                        .filter { sectorId == null || it.sectorId == sectorId }
                    (sectorId == null || visibleLots.isNotEmpty()) && matches(row.product)
                }
                if (isBuy) item { Text("A reposição considera o saldo disponível em todos os setores.") }
                if (rows.isEmpty()) item { EmptyStock(if (isBuy) "Nenhum produto para comprar." else "Nenhum produto encontrado.") }
                items(rows, key = { "product-${it.product.id}" }) { row ->
                    val product = row.product
                    val sectorTotal = lotsByProduct[product.id].orEmpty()
                        .filter { it.sectorId == sectorId }.sumOf { it.quantity.toLong() }
                    OutlinedCard(onClick = { onAdjust(product.id) }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        StockCardContent(repository, product) {
                            product.barcode?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (isBuy) Text("Disponível: ${row.available} un. · Mínimo: ${product.minimum} un.")
                            else Text("Saldo total: ${row.total} un.")
                            if (!isBuy && sectorId != null) Text("Neste setor: $sectorTotal un.")
                            if (!isBuy && row.available != row.total) Text("Disponível: ${row.available} un.")
                        }
                    }
                }
            }
            StockList.NEAR, StockList.EXPIRED -> {
                val isExpired = list == StockList.EXPIRED
                val lots = (if (isExpired) summary.expired else summary.nearExpiry).filter { lot ->
                    val product = productsById[lot.productId]
                    product != null && (sectorId == null || lot.sectorId == sectorId) && matches(product)
                }
                if (lots.isEmpty()) item { EmptyStock(if (isExpired) "Nenhum lote vencido." else "Nenhum lote vence nos próximos 7 dias.") }
                items(lots, key = { "lot-${it.id}" }) { lot ->
                    OutlinedCard(onClick = { onEdit(lot) }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        StockCardContent(repository, productsById.getValue(lot.productId)) {
                            Text("${sectorsById.getValue(lot.sectorId).name} · ${lot.quantity} un.")
                            Text(
                                "Validade: ${lot.expiration}",
                                color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStock(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Layout.gap),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_mv_empty_box),
                contentDescription = null,
                modifier = Modifier.size(width = 128.dp, height = 98.dp).padding(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(message, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StockCardContent(repository: InventoryRepository, product: Product, details: @Composable () -> Unit) {
    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(repository, product)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            product.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            details()
        }
    }
}
