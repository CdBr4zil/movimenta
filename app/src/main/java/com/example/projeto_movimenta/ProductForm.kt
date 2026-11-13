package com.example.projeto_movimenta

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.projeto_movimenta.ui.theme.Layout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal enum class ProductFormMode { NEW_PRODUCT, NEW_LOT, EDIT_PRODUCT, EDIT_LOT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductForm(
    mode: ProductFormMode,
    inventory: Inventory,
    initialBarcode: String,
    initialProduct: Product?,
    existingLot: StockLot?,
    repository: InventoryRepository,
    busy: Boolean,
    modifier: Modifier,
    createdSectorId: Long?,
    onSectorAcknowledged: () -> Unit,
    onCreateSector: (String) -> Unit,
    onSave: (Product, StockLot?, PhotoChange) -> Unit,
    onExistingProduct: (Long) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editingLot = mode == ProductFormMode.EDIT_LOT
    val editingProduct = mode == ProductFormMode.EDIT_PRODUCT
    val hasLot = !editingProduct
    var selectedProductId by rememberSaveable { mutableStateOf(initialProduct?.id) }
    val selectedProduct = inventory.products.find { it.id == selectedProductId }
    val emptyCatalog = mode == ProductFormMode.NEW_LOT && inventory.products.isEmpty()
    val sharedFields = editingProduct || mode == ProductFormMode.NEW_PRODUCT
    var name by rememberSaveable { mutableStateOf(if (editingProduct) initialProduct?.name.orEmpty() else "") }
    var barcode by rememberSaveable { mutableStateOf(if (editingProduct) initialProduct?.barcode.orEmpty() else initialBarcode) }
    var brand by rememberSaveable { mutableStateOf(initialProduct?.brand.orEmpty()) }
    var minimum by rememberSaveable { mutableStateOf(if (editingProduct) (initialProduct?.minimum ?: 0).toString() else "0") }
    var sectorId by rememberSaveable { mutableStateOf(existingLot?.sectorId) }
    val sector = inventory.sectors.find { it.id == sectorId } ?: inventory.sectors.singleOrNull()
    var quantity by rememberSaveable { mutableStateOf("") }
    var boxes by rememberSaveable { mutableStateOf(false) }
    var perBox by rememberSaveable { mutableStateOf((existingLot?.unitsPerBox ?: 1).toString()) }
    var delivery by rememberSaveable { mutableStateOf(DateInput.digits(existingLot?.delivery ?: StockRules.today())) }
    var expiration by rememberSaveable { mutableStateOf(DateInput.digits(existingLot?.expiration.orEmpty())) }
    var noExpiration by rememberSaveable { mutableStateOf(editingLot && existingLot?.expiration == null) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var chooseProduct by rememberSaveable { mutableStateOf(false) }
    var addSector by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(createdSectorId) {
        if (createdSectorId != null) {
            sectorId = createdSectorId
            addSector = false
            onSectorAcknowledged()
        }
    }

    var photoCameraOpen by remember { mutableStateOf(false) }
    var scannerOpen by remember { mutableStateOf(false) }
    var codeFeedback by remember { mutableStateOf<String?>(null) }
    var lookupBusy by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<FoodSuggestion?>(null) }
    var lookupFeedback by remember { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var photoFeedback by remember { mutableStateOf<String?>(null) }
    var photoBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var photoSource by rememberSaveable { mutableStateOf<String?>(null) }
    var photoLookupBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestedNameAccepted by rememberSaveable { mutableStateOf(false) }
    var suggestedBrandAccepted by rememberSaveable { mutableStateOf(false) }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    val savedPhoto = rememberProductPhoto(repository, selectedProduct)
    val currentPhoto = if (sharedFields) {
        photoBytes?.let { ProductPhoto(it, photoSource) } ?: savedPhoto.takeUnless { photoRemoved }
    } else {
        savedPhoto
    }

    fun changeBarcode(value: String) {
        val next = numericInput(value)
        if (next == barcode) return
        if (suggestedNameAccepted) name = ""
        if (suggestedBrandAccepted) brand = ""
        if (photoLookupBarcode != null) {
            photoBytes = null
            photoSource = null
            photoLookupBarcode = null
        }
        suggestedNameAccepted = false
        suggestedBrandAccepted = false
        barcode = next
        suggestion = null
        lookupFeedback = null
        codeFeedback = null
    }

    fun loadPhoto(source: String?, requestedCode: String? = null, load: () -> ByteArray) {
        if (photoBusy) return
        photoBusy = true
        photoFeedback = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { load() }
                if (requestedCode != null && requestedCode != barcode) return@launch
                photoBytes = loaded
                photoSource = source
                photoLookupBarcode = requestedCode
                photoRemoved = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                photoFeedback = "Não foi possível carregar a foto. Você pode escolher outra ou continuar."
            } finally {
                photoBusy = false
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadPhoto(null) {
            preparePhoto {
                context.contentResolver.openInputStream(uri) ?: throw IOException("Foto indisponível.")
            }
        }
    }
    fun choosePhoto() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val openScanner = rememberCameraAccess(
        onGranted = { scannerOpen = true },
        onDenied = { codeFeedback = "Permissão da câmera negada. Você pode digitar o código." },
    )
    val openPhotoCamera = rememberCameraAccess(
        onGranted = { photoCameraOpen = true },
        onDenied = { photoFeedback = "Permissão da câmera negada. Você pode escolher uma foto da galeria." },
    )

    val duplicate = inventory.findByBarcode(barcode)?.takeUnless { it.id == selectedProductId }
    val minimumValue = StockRules.integer(minimum)
    val boxSize = StockRules.integer(perBox)?.takeIf { it > 0 }
    val units = boxSize?.let { StockRules.units(quantity, boxes, it) }
    val deliveryValue = DateInput.formatted(delivery)
    val expirationValue = if (noExpiration) null else DateInput.formatted(expiration)
    val dates = StockRules.dateErrors(deliveryValue, expirationValue)
    val validProduct = if (sharedFields) name.isNotBlank() && minimumValue != null && duplicate == null else selectedProduct != null
    val validLot = !hasLot || (sector != null && boxSize != null && dates.isEmpty() && (editingLot || units != null))

    Column(
        modifier.imePadding().verticalScroll(rememberScrollState()).padding(Layout.gutter),
        verticalArrangement = Arrangement.spacedBy(Layout.gap),
    ) {
        if (!sharedFields) {
            selectedProduct?.let {
                Text(it.name, style = MaterialTheme.typography.titleLarge)
                it.barcode?.let { code -> Text(code) }
            }
            if (mode == ProductFormMode.NEW_LOT && initialProduct == null) {
                SecondaryAction(
                    if (selectedProduct == null) "Selecionar produto" else "Trocar produto",
                    { chooseProduct = true }, R.drawable.ic_mv_box, Modifier.fillMaxWidth(),
                    enabled = !busy && !emptyCatalog,
                )
                if (emptyCatalog) Text(
                    "Cadastre um produto antes de adicionar outro lote.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sharedFields) {
            SectionCard() {
                ProductField(
                    "Código de barras", barcode, ::changeBarcode,
                    error = if (duplicate != null) "Este código já pertence a ${duplicate.name}." else null,
                    enabled = !busy, keyboard = KeyboardType.Number, icon = R.drawable.ic_mv_barcode,
                )
                if (duplicate != null && !editingProduct) {
                    OutlinedButton(onClick = { onExistingProduct(duplicate.id) }, enabled = !busy && !photoBusy) {
                        Text("Novo lote de ${duplicate.name}")
                    }
                }
                ActionButton("Escanear código de barras", openScanner, R.drawable.ic_mv_scan, Modifier.fillMaxWidth(), enabled = !busy)
                codeFeedback?.let { Text(it) }
                if (!editingProduct) {
                    SecondaryAction(
                        if (lookupBusy) "Buscando produto…" else "Buscar produto na Open Food Facts",
                        icon = R.drawable.ic_mv_search,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val requestedCode = barcode
                            lookupBusy = true
                            suggestion = null
                            lookupFeedback = null
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) { OpenFoodFacts.lookup(requestedCode) }
                                    if (barcode == requestedCode && selectedProductId == null) {
                                        suggestion = result?.takeIf { it.name != null || it.image != null || it.brand != null }
                                        lookupFeedback = when {
                                            result == null -> "Produto não encontrado. Continue com o cadastro manual."
                                            suggestion == null -> "O catálogo não tem informações para este produto. Continue com o cadastro manual."
                                            result.name == null || result.image == null || result.brand == null -> "O catálogo só tem parte das informações deste produto."
                                            else -> null
                                        }
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    if (barcode == requestedCode && selectedProductId == null) {
                                        lookupFeedback = "Não foi possível consultar a Open Food Facts. Continue manualmente ou tente novamente."
                                    }
                                } finally {
                                    lookupBusy = false
                                }
                            }
                        },
                        enabled = !busy && !lookupBusy && duplicate == null && StockRules.isBarcode(barcode),
                    )
                    lookupFeedback?.let { Text(it) }
                    suggestion?.let { result ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Sugestão da Open Food Facts", style = MaterialTheme.typography.titleSmall)
                                result.name?.let { suggestedName ->
                                    Text(suggestedName)
                                    TextButton(onClick = {
                                        name = suggestedName
                                        suggestedNameAccepted = true
                                    }, enabled = !busy && name != suggestedName) {
                                        Text("Usar nome sugerido")
                                    }
                                }
                                result.brand?.let { suggestedBrand ->
                                    Text(suggestedBrand)
                                    TextButton(onClick = {
                                        brand = suggestedBrand
                                        suggestedBrandAccepted = true
                                    }, enabled = !busy && brand != suggestedBrand) {
                                        Text("Usar marca sugerida")
                                    }
                                }
                                result.image?.let { image ->
                                    TextButton(
                                        onClick = { loadPhoto(result.code, barcode) {
                                            val bytes = OpenFoodFacts.downloadImage(image)
                                            preparePhoto { bytes.inputStream() }
                                        } },
                                        enabled = !busy && !photoBusy,
                                    ) { Text("Usar foto sugerida") }
                                }
                            }
                        }
                    }
                }
                ProductField("Nome do produto", name, {
                    name = it
                    suggestedNameAccepted = false
                }, "Informe o nome do produto.".takeIf { submitted && name.isBlank() }, !busy, icon = R.drawable.ic_mv_tag)
                ProductField("Marca (opcional)", brand, {
                    brand = it
                    suggestedBrandAccepted = false
                }, enabled = !busy, icon = R.drawable.ic_mv_brand)
                ProductField(
                    "Mínimo em unidades", minimum, { minimum = it },
                    "Informe um número inteiro igual ou maior que zero.".takeIf { submitted && minimumValue == null },
                    !busy, KeyboardType.Number, icon = R.drawable.ic_mv_layers,
                )
                currentPhoto?.let { ProductPhotoPreview(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Layout.compactGap)) {
                    SecondaryAction("Tirar foto", openPhotoCamera, R.drawable.ic_mv_camera, enabled = !busy && !photoBusy)
                    SecondaryAction(if (photoBusy) "Carregando foto…" else "Escolher foto", ::choosePhoto,
                        R.drawable.ic_mv_photo, enabled = !busy && !photoBusy)
                }
                if (currentPhoto != null) {
                    TextButton(onClick = {
                        photoBytes = null
                        photoSource = null
                        photoLookupBarcode = null
                        photoRemoved = true
                        photoFeedback = null
                    }, enabled = !busy && !photoBusy) { Text("Remover foto") }
                }
                photoFeedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (hasLot) {
            SectionCard("Lote", R.drawable.ic_mv_box) {
                OptionPicker("Setor", sector?.name ?: "Escolha um setor", inventory.sectors.map { it.id to it.name }, enabled = !busy, icon = R.drawable.ic_mv_layers) {
                    sectorId = it
                }
                if (submitted && sector == null) Text("Escolha ou crie um setor.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { addSector = true }, enabled = !busy) { Text("Criar setor") }
                DateField("Data de entrega", delivery, dates["delivery"].takeIf { submitted }, !busy) { delivery = it }
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).toggleable(
                        value = noExpiration, enabled = !busy, role = Role.Checkbox, onValueChange = { noExpiration = it },
                    ).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = noExpiration, onCheckedChange = null, enabled = !busy)
                    Text("Sem validade")
                }
                if (!noExpiration) {
                    DateField("Data de validade", expiration, dates["expiration"].takeIf { submitted }, !busy) { expiration = it }
                }
                ProductField(
                    "Unidades por caixa", perBox, { perBox = it },
                    "Informe um número inteiro maior que zero.".takeIf { submitted && boxSize == null }, !busy, KeyboardType.Number, icon = R.drawable.ic_mv_box,
                )
                if (editingLot && existingLot != null) {
                    Text("Saldo do lote: ${existingLot.quantity} un.")
                } else {
                    MeasurePicker(boxes, !busy) { boxes = it }
                    ProductField(
                        if (boxes) "Quantidade de caixas" else "Quantidade em unidades",
                        quantity, { quantity = it },
                        "Informe uma quantidade inteira dentro do limite.".takeIf { submitted && units == null }, !busy, KeyboardType.Number, icon = R.drawable.ic_mv_layers,
                    )
                    if (boxes && units != null) Text("Total: $units un.")
                }
            }
        }
        if (submitted && (!validProduct || !validLot)) {
            val fields = buildList {
                if (sharedFields && name.isBlank()) add("nome")
                if (sharedFields && minimumValue == null) add("mínimo")
                if (sharedFields && duplicate != null) add("código de barras")
                if (!sharedFields && selectedProduct == null) add("produto")
                if (hasLot) {
                    if (sector == null) add("setor")
                    if ("delivery" in dates) add("data de entrega")
                    if ("expiration" in dates) add("validade")
                    if (boxSize == null) add("unidades por caixa")
                    if (!editingLot && units == null && boxSize != null) add("quantidade")
                }
            }
            Text("Revise os campos: ${fields.joinToString()}.", color = MaterialTheme.colorScheme.error)
        }
        ActionButton(
            text = when (mode) {
                ProductFormMode.NEW_PRODUCT -> "Confirmar cadastro"
                ProductFormMode.NEW_LOT -> "Adicionar lote"
                else -> "Salvar alterações"
            },
            icon = if (mode == ProductFormMode.NEW_LOT) R.drawable.ic_add_lot else R.drawable.ic_mv_check,
            onClick = {
                submitted = true
                if (validProduct && validLot) {
                    val product = if (sharedFields) Product(
                        id = selectedProduct?.id ?: 0,
                        name = name.trim(),
                        barcode = barcode.ifEmpty { null },
                        minimum = requireNotNull(minimumValue),
                        brand = brand.trim().ifEmpty { null },
                    ) else requireNotNull(selectedProduct)
                    val lot = if (hasLot) StockLot(
                        id = existingLot?.id ?: 0, productId = product.id, sectorId = requireNotNull(sector).id,
                        quantity = existingLot?.quantity ?: requireNotNull(units), unitsPerBox = requireNotNull(boxSize),
                        delivery = deliveryValue, expiration = expirationValue,
                    ) else null
                    val photo = when {
                        !sharedFields -> PhotoChange.Keep
                        photoBytes != null -> PhotoChange.Replace(ProductPhoto(requireNotNull(photoBytes), photoSource))
                        photoRemoved -> PhotoChange.Remove
                        else -> PhotoChange.Keep
                    }
                    onSave(product, lot, photo)
                }
            },
            enabled = !emptyCatalog && !busy && (!sharedFields || !photoBusy),
            modifier = Modifier.fillMaxWidth(),
        )

        if (onRemove != null) {
            TextButton(onClick = { confirmRemove = true }, enabled = !busy) {
                Text("Excluir lote", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (scannerOpen) {
        BarcodeScannerDialog(onResult = { code ->
            scannerOpen = false
            changeBarcode(code)
        }, onClose = { scannerOpen = false })
    }
    if (photoCameraOpen) {
        ProductCameraDialog(
            onUse = {
                photoBytes = it
                photoSource = null
                photoLookupBarcode = null
                photoRemoved = false
                photoFeedback = null
                photoCameraOpen = false
            },
            onGallery = {
                photoCameraOpen = false
                choosePhoto()
            },
            onClose = { photoCameraOpen = false },
        )
    }
    if (chooseProduct) {
        ProductPicker(inventory.products, onClose = { chooseProduct = false }) {
            selectedProductId = it
            chooseProduct = false
        }
    }
    if (addSector) {
        NewSectorDialog(busy, onClose = { addSector = false }, onCreate = onCreateSector)
    }
    if (confirmRemove && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Excluir este lote?") },
            text = { Text("O saldo deste lote será removido do estoque.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProductPicker(products: List<Product>, onClose: () -> Unit, onSelect: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filter = query.trim()
    val matches = products.filter { it.name.contains(filter, ignoreCase = true) || it.barcode?.contains(filter) == true }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Selecionar produto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductField("Buscar por nome ou código", query, { query = it })
                if (matches.isEmpty()) Text("Nenhum produto encontrado.")
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(matches, key = { it.id }) { product ->
                        ListItem(
                            headlineContent = { Text(product.name) },
                            supportingContent = product.barcode?.let { code -> { Text(code) } },
                            modifier = Modifier.clickable { onSelect(product.id) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}

@Composable
private fun NewSectorDialog(busy: Boolean, onClose: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Criar setor") },
        text = { ProductField("Nome do setor", name, { name = it }, enabled = !busy) },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank() && !busy) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancelar") } },
    )
}
