# Movimenta

Aplicativo Android em Kotlin e Jetpack Compose para controlar o estoque de mercados pequenos e médios. Produtos, setores, lotes e fotos são salvos em SQLite no aparelho.

## Uso

- **Início** mostra saldos, fotos e marcas dos produtos. Busque por nome, marca ou código de barras, filtre por setor ou escaneie o código para abrir um produto do estoque. Se o código ainda não estiver cadastrado, abra o cadastro com o número preenchido.
- **Novo produto** cadastra um produto e seu primeiro lote. Digite ou escaneie o código de barras. Com internet, a Open Food Facts pode sugerir nome, marca e foto pelo código completo. Você também pode tirar uma foto no app ou escolher uma da galeria.
- **Novo lote** seleciona um produto cadastrado e registra outra entrega, com setor, quantidade, unidades por caixa e datas próprias. Duas caixas de 12 entram como 24 unidades.
- No **detalhe do produto**, escolha um lote para ajustar seu saldo ou corrigir suas informações. Use **Editar produto** para mudar os dados compartilhados. **Excluir produto** pede confirmação e remove o produto, seus lotes e sua foto.

**Comprar** considera o saldo disponível em todos os setores, excluindo lotes vencidos. **Perto da validade** lista lotes com saldo que vencem de hoje até os próximos sete dias, inclusive. **Vencidos** lista lotes com saldo cuja data já passou.

Ao atualizar uma instalação anterior, o app migra o estoque, incluindo fotos e códigos de barras. Os valores antigos de QR Code são descartados. Em uma atualização da primeira versão, lotes com o mesmo código compartilham o nome mais recente e entram inicialmente em **Sem setor**.

## Desenvolvimento e verificação

Use o JDK e o Android SDK indicados pela configuração Gradle do projeto. No Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

O segundo comando requer aparelho ou emulador Android. Para verificar a Open Food Facts, digite `3017620422003` em **Novo produto** e confira as sugestões. Depois tire uma foto, refaça a captura e use a segunda imagem. Salve e reabra o app para conferir a miniatura. Em modo avião, escaneie um código já cadastrado pela Home para abrir seus detalhes.
