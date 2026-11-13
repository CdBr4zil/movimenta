# Movimenta

Movimenta é um aplicativo Android em Kotlin e Jetpack Compose para controlar o estoque de pequenos e médios mercados. Cadastre produtos por código de barras, organize lotes por setor e acompanhe saldos e prazos de validade.

Produtos, setores, lotes e fotos ficam no SQLite do aparelho. A consulta à [Open Food Facts](https://world.openfoodfacts.org/) é opcional e usa internet para sugerir nome, marca e foto ao informar um código de barras.

## Uso

- **Início** mostra saldos, fotos e marcas dos produtos. Busque por nome, marca ou código de barras, filtre por setor ou escaneie o código para abrir um produto do estoque. Se o código ainda não estiver cadastrado, abra o cadastro com o número preenchido.
- **Novo produto** cadastra um produto e seu primeiro lote. Digite ou escaneie o código de barras. Com internet, a Open Food Facts pode sugerir nome, marca e foto pelo código completo. Você também pode tirar uma foto no app ou escolher uma da galeria.
- **Novo lote** seleciona um produto cadastrado e registra outra entrega, com setor, quantidade, unidades por caixa e datas próprias. Duas caixas de 12 entram como 24 unidades.
- No **detalhe do produto**, escolha um lote para ajustar seu saldo ou corrigir suas informações. Use **Editar produto** para mudar os dados compartilhados. **Excluir produto** pede confirmação e remove o produto, seus lotes e sua foto.

**Comprar** considera o saldo disponível em todos os setores, excluindo lotes vencidos. **Perto da validade** lista lotes com saldo que vencem de hoje até os próximos sete dias, inclusive. **Vencidos** lista lotes com saldo cuja data já passou.

Ao atualizar uma instalação anterior, o app migra o estoque, incluindo fotos e códigos de barras. Os valores antigos de QR Code são descartados. Em uma atualização da primeira versão, lotes com o mesmo código compartilham o nome mais recente e entram inicialmente em **Sem setor**.

## Como rodar

Use o Android Studio com JDK 25 e Android SDK API 37. Para instalar o app, use um aparelho ou emulador com Android 7.0 (API 24) ou superior.

1. No Android Studio, escolha **Get from Version Control** na tela inicial ou **File → New → Project from Version Control**. Cole `https://github.com/CdBr4zil/movimenta.git` e clique em **Clone**. Use uma conta com acesso ao repositório privado.
2. Abra a raiz do clone, onde está `settings.gradle.kts`. No **SDK Manager**, instale a plataforma Android API 37. Em **Settings → Build, Execution, Deployment → Build Tools → Gradle**, confira se o JDK usado pelo Gradle é o 25.
3. Sincronize o projeto com **File → Sync Project with Gradle Files**. Selecione a configuração `app` e um aparelho ou emulador. Clique em **Run**.

## Testes e verificação

No terminal do Android Studio, execute os comandos abaixo no Windows.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

O segundo comando requer aparelho ou emulador Android. Para verificar a Open Food Facts, digite `3017620422003` em **Novo produto** e confira as sugestões. Depois tire uma foto, refaça a captura e use a segunda imagem. Salve e reabra o app para conferir a miniatura. Em modo avião, escaneie um código já cadastrado pela Home para abrir seus detalhes.

## Versionamento

O número exibido pelo Android (`versionName`) usa três partes. Por exemplo, `1.0.1` corrige erros, `1.1.0` adiciona funcionalidades compatíveis e `2.0.0` identifica mudanças incompatíveis. Aumente o `versionCode` a cada build distribuído, a partir do código `4` usado na versão `1.0.0`.

Após validar o build e enviar o commit para `master`, crie uma release no GitHub com a tag da versão, como `v1.0.0`, e registre as mudanças nas notas da release.
