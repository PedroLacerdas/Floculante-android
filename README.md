# Tela de controle de floculantes (Android, XML + Kotlin)

## Instalar no projeto
1. Copie os 3 arquivos para as mesmas pastas do seu `app/src/main/` (layout, values e a pasta do código).
2. Em `FloculantesActivity.kt`, troque `com.suaempresa.app` pelo package do projeto (linhas `package` e `import ...R`).
3. Registre no `AndroidManifest.xml`, dentro de `<application>`:
   `<activity android:name=".floculantes.FloculantesActivity" android:exported="false" />`
4. Dependências (`app/build.gradle`), usando as versões que o projeto já tem: `com.google.firebase:firebase-firestore` (com o BoM do Firebase) e `com.google.android.material:material`. O tema do app deve ser AppCompat/Material.
5. Abra a tela de onde quiser: `startActivity(Intent(this, FloculantesActivity::class.java))`.
6. Regras do Firestore: libere as coleções `floculante_registros`, `floculante_estoque`, `floculante_entradas` e `floculante_testes` (mesmo padrão das outras coleções).

## Modo administrador
- O campo de concentração e o gráfico de concentração só aparecem depois de entrar pelo menu "Administrador" (canto superior direito) com o PIN.
- O PIN está em `PIN_ADMIN`, no fim de `FloculantesActivity.kt` (padrão `1234`). O modo desliga ao fechar a tela.
- O PIN só esconde os itens na tela; não impede a leitura dos dados no Firestore.

## Como os números são calculados (últimos 30 dias)
- Cada registro de turno informa vazão média (L/h) e consumo (kg); o administrador pode informar também a concentração (%), que é só para controle e não entra em nenhum cálculo.
- Cada registro de turno debita o consumo informado do estoque; a entrada de estoque soma e guarda o lote e se a coleta do floculante foi feita.
- Consumo médio diário = consumo total / dias com registro. Dias de estoque = estoque / consumo médio diário.
- Gráficos por turno (X, Y e Z): médias simples da concentração, da vazão e do consumo dos registros de cada turno.
