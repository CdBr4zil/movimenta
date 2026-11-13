package com.example.projeto_movimenta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.projeto_movimenta.ui.theme.Layout

@Composable
internal fun ProductManagementScreen(
    product: Product,
    lots: List<StockLot>,
    sectors: List<Sector>,
    repository: InventoryRepository,
    busy: Boolean,
    modifier: Modifier,
    adjustedLotId: Long?,
    onAdjustmentAcknowledged: () -> Unit,
    onAdjust: (Long, Int, Boolean, StockDirection) -> Unit,
    onNewLot: () -> Unit,
    onEdit: (StockLot) -> Unit,
    onEditProduct: () -> Unit,
    onRemoveProduct: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val total = lots.sumOf { it.quantity.toLong() }
    val productSectors = sectors.filter { sector -> lots.any { it.sectorId == sector.id } }
    var sectorId by rememberSaveable(product.id) { mutableStateOf<Long?>(null) }
    val sector = productSectors.find { it.id == sectorId } ?: productSectors.firstOrNull()
    val sectorLots = lots.filter { it.sectorId == sector?.id }
    var lotId by rememberSaveable(product.id) { mutableStateOf<Long?>(null) }
    val lot = sectorLots.find { it.id == lotId } ?: sectorLots.firstOrNull()
    LaunchedEffect(adjustedLotId, lot?.id) {
        if (adjustedLotId != null && adjustedLotId != lot?.id) onAdjustmentAcknowledged()
    }

    Column(
        modifier.imePadding().verticalScroll(rememberScrollState()).padding(Layout.gutter),
        verticalArrangement = Arrangement.spacedBy(Layout.gap),
    ) {
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Layout.gap)) {
                ProductThumbnail(repository, product)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.name, style = MaterialTheme.typography.headlineSmall)
                    product.brand?.let { Text(it) }
                    product.barcode?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val photo = rememberProductPhoto(repository, product)
            photo?.sourceCode?.let { ProductPhotoCredit(it) }
            Text("Saldo em todos os setores: $total un.", style = MaterialTheme.typography.titleMedium)
            SecondaryAction("Adicionar novo lote", onNewLot, R.drawable.ic_add_lot, Modifier.fillMaxWidth(), !busy)
            SecondaryAction("Editar produto", onEditProduct, R.drawable.ic_mv_tag, Modifier.fillMaxWidth(), !busy)
        }

        if (lot == null || sector == null) {
            Text("Este produto ainda não tem lotes.")
        } else {
            SectionCard("Lote", R.drawable.ic_mv_box) {
                OptionPicker("Setor", sector.name, productSectors.map { it.id to it.name }, enabled = !busy, icon = R.drawable.ic_mv_layers) {
                    sectorId = it
                    lotId = null
                }
                OptionPicker("Lote", lotLabel(lot), sectorLots.map { it.id to lotLabel(it) }, enabled = !busy, icon = R.drawable.ic_mv_box) { lotId = it }
                Text("Saldo neste lote: ${lot.quantity} un. · ${lot.unitsPerBox} un. por caixa")
                SecondaryAction("Editar este lote", { onEdit(lot) }, R.drawable.ic_mv_tag, Modifier.fillMaxWidth(), !busy)
                HorizontalDivider()
                key(lot.id) {
                    QuantityAdjustment(lot, busy, adjustedLotId, onAdjustmentAcknowledged, onAdjust)
                }
            }
        }
        TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
            Text("Excluir produto", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${product.name}?") },
            text = { Text("Serão removidos ${lots.size} lote(s) e $total unidade(s) do estoque.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onRemoveProduct() }, enabled = !busy) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun QuantityAdjustment(
    lot: StockLot,
    busy: Boolean,
    adjustedLotId: Long?,
    onAdjustmentAcknowledged: () -> Unit,
    onAdjust: (Long, Int, Boolean, StockDirection) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(StockDirection.IN) }
    var boxes by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(adjustedLotId) {
        if (adjustedLotId == lot.id) {
            amount = ""
            onAdjustmentAcknowledged()
        }
    }
    val units = StockRules.units(amount, boxes, lot.unitsPerBox)
    val next = units?.let { StockRules.adjusted(lot.quantity, it, direction) }
    val error = when {
        amount.isBlank() -> null
        units == null -> "Informe uma quantidade inteira dentro do limite."
        next == null && direction == StockDirection.OUT -> "A saída é maior que o saldo do lote."
        next == null -> "O saldo ultrapassa o limite permitido."
        else -> null
    }
    OptionPicker(
        "Movimentação", if (direction == StockDirection.IN) "Entrada" else "Saída",
        listOf(StockDirection.IN to "Entrada", StockDirection.OUT to "Saída"),
        enabled = !busy,
    ) { direction = it }
    MeasurePicker(boxes, !busy) { boxes = it }
    ProductField(
        if (boxes) "Quantidade de caixas" else "Quantidade em unidades",
        amount, { amount = it }, error, !busy, KeyboardType.Number,
    )
    if (next != null) Text("Saldo previsto: $next un.")
    ActionButton(
        text = "Confirmar ajuste",
        icon = R.drawable.ic_mv_check,
        onClick = {
            StockRules.integer(amount)?.let { onAdjust(lot.id, it, boxes, direction) }
        },
        enabled = !busy && next != null,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun lotLabel(lot: StockLot): String =
    "Lote ${lot.id} · ${lot.expiration?.let { "validade $it" } ?: "sem validade"}"
