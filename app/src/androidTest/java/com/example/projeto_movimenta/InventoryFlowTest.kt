package com.example.projeto_movimenta

import android.Manifest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.projeto_movimenta.ui.theme.Projeto_MovimentaTheme
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class InventoryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyCatalogDisablesAddingALot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "movimenta-empty-${UUID.randomUUID()}.db"
        try {
            InventoryRepository(context, database).use { repository ->
                val controller = InventoryController(repository)
                compose.setContent { Projeto_MovimentaTheme { InventoryApp(controller) } }
                awaitText("Nenhum produto encontrado.")
                compose.onNodeWithText("Novo lote").performClick()
                compose.onNodeWithText("Selecionar produto").assertIsNotEnabled()
                compose.onNode(hasText("Adicionar lote") and hasClickAction()).assertIsNotEnabled()
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test fun startupShowsLoadingAndAllowsRetryAfterFailure() {
        val failed = androidx.compose.runtime.mutableStateOf(false)
        var retried = false
        compose.setContent {
            Projeto_MovimentaTheme { StartupContent(failed.value, busy = false, onRetry = { retried = true }) }
        }
        compose.onNodeWithText("Carregando estoque…").assertIsDisplayed()
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithText("Não foi possível carregar o estoque.").assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(retried) }
    }

    @Test fun registersBoxesAdjustsTheLotAndEditsItsExpiry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "movimenta-flow-${UUID.randomUUID()}.db"
        InventoryRepository(context, database).use { repository ->
            try {
                val controller = InventoryController(repository)
                compose.setContent { Projeto_MovimentaTheme { InventoryApp(controller) } }
                awaitText("Novo produto")
                compose.onNodeWithText("Novo produto").performClick()
                enter("Código de barras", "000123")
                enter("Nome do produto", "Leite")
                enter("Mínimo em unidades", "25")
                click("Criar setor")
                enter("Nome do setor", "Câmara fria", scroll = false)
                compose.onNodeWithText("Criar", substring = false).performClick()
                awaitText("Câmara fria")
                enter("Data de validade", DateInput.digits(StockRules.today()))
                enter("Unidades por caixa", "12")
                click("Caixas")
                enter("Quantidade de caixas", "2")
                submit("Confirmar cadastro")
                awaitText("Saldo total: 24 un.")
                compose.onNodeWithText("Leite").performClick()
                awaitText("Detalhes do produto")
                click("Movimentação")
                compose.onNodeWithText("Saída").performClick()
                click("Caixas")
                enter("Quantidade de caixas", "3")
                compose.onNodeWithText("Confirmar ajuste").assertIsNotEnabled()
                enter("Quantidade de caixas", "1")
                submit("Confirmar ajuste")
                awaitText("Saldo neste lote: 12 un. · 12 un. por caixa")
                click("Editar este lote")
                awaitText("Editar lote")
                click("Sem validade")
                enter("Unidades por caixa", "10")
                submit("Salvar alterações")
                awaitText("Saldo neste lote: 12 un. · 10 un. por caixa")
                compose.onNodeWithContentDescription("Voltar").performClick()
                compose.onNodeWithText("Comprar").performClick()
                awaitText("Disponível: 12 un. · Mínimo: 25 un.")
                compose.onNodeWithText("Novo lote").performClick()
                click("Selecionar produto")
                compose.onNodeWithText("Leite").performClick()
                click("Sem validade")
                enter("Quantidade em unidades", "3")
                submit("Adicionar lote")
                awaitText("Estoque")
                compose.onNodeWithText("Produtos").performClick()
                awaitText("Saldo total: 15 un.")
                compose.onNodeWithText("Leite").performClick()
                awaitText("Detalhes do produto")
                compose.onNode(hasText("Excluir produto") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
                awaitText("Serão removidos 2 lote(s) e 15 unidade(s) do estoque.")
                compose.onNodeWithText("Cancelar").performClick()
                awaitText("Saldo em todos os setores: 15 un.")
                compose.onNode(hasText("Excluir produto") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
                compose.onNodeWithText("Excluir").performClick()
                awaitText("Nenhum produto encontrado.")
            } finally {
                repository.close()
                context.deleteDatabase(database)
            }
        }
    }

    @Test fun scansLocalBarcodeEvenWhenFilteredAndPrefillsUnknownBarcode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val database = "movimenta-scan-${UUID.randomUUID()}.db"
        try {
            InventoryRepository(context, database).use { repository ->
                val sector = repository.createSector("Depósito")
                val lot = StockLot(sectorId = sector.id, quantity = 1, unitsPerBox = 1, delivery = StockRules.today(), expiration = null)
                repository.register(Product(name = "Sabão", barcode = "000123"), lot)
                repository.register(Product(name = "Farinha", barcode = "000124"), lot)
                val controller = InventoryController(repository)
                compose.setContent {
                    Projeto_MovimentaTheme {
                        InventoryApp(controller) { result, close ->
                            AlertDialog(
                                onDismissRequest = close,
                                confirmButton = { TextButton(onClick = { result("000123") }) { Text("Ler código existente") } },
                                dismissButton = { TextButton(onClick = { result("000125") }) { Text("Ler código novo") } },
                            )
                        }
                    }
                }
                awaitText("Escanear código de barras")
                enter("Buscar por nome, marca ou código", "Farinha", scroll = false)
                compose.onNodeWithText("Escanear código de barras").performClick()
                compose.onNodeWithText("Ler código existente").performClick()
                awaitText("Detalhes do produto")
                compose.onNodeWithText("Sabão").assertExists()
                compose.onNodeWithContentDescription("Voltar").performClick()
                compose.onNodeWithText("Escanear código de barras").performClick()
                compose.onNodeWithText("Ler código novo").performClick()
                awaitText("Produto fora do estoque")
                compose.onNodeWithText("Cadastrar produto").performClick()
                compose.onNode(hasText("Código de barras") and hasSetTextAction()).assertTextContains("000125")
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    private fun submit(text: String) = compose.onNode(hasText(text) and hasClickAction())
        .performScrollTo().assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)

    private fun enter(label: String, value: String, scroll: Boolean = true) {
        val field = compose.onNode(hasText(label) and hasSetTextAction())
        if (scroll) field.performScrollTo()
        field.performTextReplacement(value)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000, condition = { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() })
    }
}
