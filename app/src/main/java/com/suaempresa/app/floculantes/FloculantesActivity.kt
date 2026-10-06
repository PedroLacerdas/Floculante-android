package com.suaempresa.app.floculantes // TROQUE pelo package do projeto

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.suaempresa.app.R // TROQUE pelo package do projeto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FloculantesActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val ptBr = Locale("pt", "BR")
    private var dataReg = ""
    private var dataTeste = ""
    private var estoqueKg = 0.0
    private var registros = listOf<Reg>()
    private lateinit var kpis: List<TextView>
    private lateinit var graficos: List<GraficoBarras>
    private var admin = false // o campo e o gráfico de concentração só aparecem no modo administrador

    data class Reg(val data: String, val turno: String, val vazaoLh: Double, val conc: Double?, val consumoKg: Double)

    private fun <T : View> v(id: Int): T = findViewById(id)
    private fun num(id: Int) = v<EditText>(id).text.toString().replace(',', '.').toDoubleOrNull()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun br(d: String) = if (d.length >= 10) "${d.substring(8)}/${d.substring(5, 7)}" else d
    private fun fmt(x: Double, casas: Int = 1) =
        if (x.isNaN() || x.isInfinite()) "–" else String.format(ptBr, "%,.${casas}f", x)
    private fun linha(t: String) = TextView(this).apply { text = t; textSize = 14f; setPadding(0, 12, 0, 12) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floculantes)
        dataReg = iso.format(Date()); dataTeste = dataReg
        configurarAbas(); configurarControle(); configurarTestes(); ouvirDados()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(if (admin) "Sair do modo administrador" else "Administrador")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (admin) definirAdmin(false) else pedirPin()
        return true
    }

    private fun pedirPin() {
        val campo = EditText(this).apply {
            hint = "PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Modo administrador").setView(campo)
            .setPositiveButton("Entrar") { _, _ ->
                if (campo.text.toString() == PIN_ADMIN) definirAdmin(true) else toast("PIN incorreto.")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun definirAdmin(ativo: Boolean) {
        admin = ativo
        v<EditText>(R.id.edConc).apply { visibility = if (ativo) View.VISIBLE else View.GONE; text.clear() }
        graficos[0].visibility = if (ativo) View.VISIBLE else View.GONE
        invalidateOptionsMenu(); atualizar()
    }

    private fun configurarAbas() {
        val tabs = v<TabLayout>(R.id.tabs)
        tabs.addTab(tabs.newTab().setText("Controle")); tabs.addTab(tabs.newTab().setText("Testes"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                v<View>(R.id.painelControle).visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                v<View>(R.id.painelTeste).visibility = if (tab.position == 1) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun ligarData(btn: Button, get: () -> String, set: (String) -> Unit) {
        btn.text = "Data: " + br(get())
        btn.setOnClickListener {
            val c = Calendar.getInstance().apply { time = iso.parse(get())!! }
            DatePickerDialog(this, { _, a, m, d ->
                val n = String.format(Locale.US, "%04d-%02d-%02d", a, m + 1, d)
                set(n); btn.text = "Data: " + br(n)
            }, c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH]).show()
        }
    }

    private fun configurarControle() {
        ligarData(v(R.id.btnData), { dataReg }, { dataReg = it })
        v<Spinner>(R.id.spTurno).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("X", "Y", "Z"))

        // Cards de indicadores (ordem usada em atualizar())
        val titulos = listOf("Estoque (kg)", "Consumo médio diário (kg)", "Dias de estoque")
        val grid = v<GridLayout>(R.id.gridKpi)
        kpis = titulos.map { titulo ->
            val valor = TextView(this).apply { text = "–"; textSize = 24f; setTypeface(null, Typeface.BOLD) }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20); background = fundoCartao(resources.displayMetrics.density)
                addView(valor); addView(TextView(context).apply { text = titulo; textSize = 13f })
            }
            val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f))
            lp.width = 0; lp.setMargins(8, 8, 8, 8)
            grid.addView(card, lp)
            valor
        }

        // Gráficos de médias por turno (ordem usada em atualizar())
        val area = v<LinearLayout>(R.id.graficos)
        graficos = listOf(GraficoBarras(this, "Concentração média (%)") { fmt(it, 2) },
            GraficoBarras(this, "Vazão média (L/h)") { fmt(it, 0) },
            GraficoBarras(this, "Consumo médio (kg)") { fmt(it) })
        graficos.forEach { g ->
            area.addView(g, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(8, 8, 8, 8) })
        }
        graficos[0].visibility = View.GONE // concentração: só no modo administrador

        v<Button>(R.id.btnSalvar).setOnClickListener {
            val vazao = num(R.id.edVazao); val consumo = num(R.id.edConsumo)
            val conc = if (admin) num(R.id.edConc) else null // opcional: só o administrador informa
            if (vazao == null || consumo == null) return@setOnClickListener toast("Preencha vazão e consumo.")
            val reg = db.collection("floculante_registros").document()
            db.batch()
                .set(reg, mapOf("data" to dataReg, "turno" to v<Spinner>(R.id.spTurno).selectedItem.toString(),
                    "vazaoLh" to vazao, "conc" to conc, "consumoKg" to consumo))
                .set(db.document("floculante_estoque/atual"), mapOf("kg" to FieldValue.increment(-consumo)), SetOptions.merge())
                .commit()
                .addOnFailureListener { toast("Falha ao salvar: ${it.message}") }
            // O Firestore grava primeiro no aparelho e sincroniza depois; não espera o servidor para liberar a tela
            toast("Registro salvo e estoque atualizado.")
            listOf(R.id.edVazao, R.id.edConc, R.id.edConsumo).forEach { v<EditText>(it).text.clear() }
        }

        v<Button>(R.id.btnEntrada).setOnClickListener {
            val kg = num(R.id.edEntrada) ?: return@setOnClickListener toast("Informe os kg recebidos.")
            val lote = v<EditText>(R.id.edLote).text.toString().trim()
            if (lote.isEmpty()) return@setOnClickListener toast("Informe o lote.")
            val coleta = v<CheckBox>(R.id.cbColeta)
            db.batch()
                .set(db.collection("floculante_entradas").document(), mapOf("data" to iso.format(Date()), "kg" to kg,
                    "lote" to lote, "coleta" to coleta.isChecked, "criadoEm" to System.currentTimeMillis()))
                .set(db.document("floculante_estoque/atual"), mapOf("kg" to FieldValue.increment(kg)), SetOptions.merge())
                .commit()
                .addOnFailureListener { toast("Falha ao salvar: ${it.message}") }
            v<EditText>(R.id.edEntrada).text.clear(); v<EditText>(R.id.edLote).text.clear(); coleta.isChecked = false
            toast("Entrada registrada.")
        }
    }

    private fun configurarTestes() {
        ligarData(v(R.id.btnDataT), { dataTeste }, { dataTeste = it })
        v<Spinner>(R.id.spStatus).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Em andamento", "Aprovado", "Reprovado"))
        v<Button>(R.id.btnSalvarT).setOnClickListener {
            val produto = v<EditText>(R.id.edProduto).text.toString().trim()
            val dose = num(R.id.edDose)
            if (produto.isEmpty() || dose == null) return@setOnClickListener toast("Informe produto e dosagem.")
            db.collection("floculante_testes").add(mapOf("data" to dataTeste, "produto" to produto, "doseGt" to dose,
                "conc" to num(R.id.edConcT), "solidosPct" to num(R.id.edSolidos),
                "material" to v<EditText>(R.id.edMaterial).text.toString().trim(), "resultado" to v<EditText>(R.id.edResultado).text.toString().trim(),
                "status" to v<Spinner>(R.id.spStatus).selectedItem.toString()))
                .addOnFailureListener { toast("Falha ao salvar: ${it.message}") }
            toast("Teste salvo.")
            listOf(R.id.edProduto, R.id.edDose, R.id.edConcT, R.id.edSolidos, R.id.edMaterial, R.id.edResultado).forEach { v<EditText>(it).text.clear() }
        }
        db.collection("floculante_testes").orderBy("data", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, _ ->
                val lista = v<LinearLayout>(R.id.listaTestes); lista.removeAllViews()
                snap?.documents?.forEach { d ->
                    lista.addView(linha("${br(d.getString("data").orEmpty())} · ${d.getString("produto").orEmpty()} · " +
                        "${fmt(d.getDouble("doseGt") ?: Double.NaN, 0)} g/t · conc. ${fmt(d.getDouble("conc") ?: Double.NaN, 2)}% · " +
                        "sólidos ${fmt(d.getDouble("solidosPct") ?: Double.NaN)}% · material: ${d.getString("material").orEmpty().ifEmpty { "–" }} · " +
                        "${d.getString("status").orEmpty()}\n${d.getString("resultado").orEmpty()}"))
                }
            }
    }

    private fun ouvirDados() {
        db.document("floculante_estoque/atual").addSnapshotListener { d, _ ->
            estoqueKg = d?.getDouble("kg") ?: 0.0; atualizar()
        }
        val desde = iso.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time) // janela de 30 dias
        db.collection("floculante_registros").whereGreaterThanOrEqualTo("data", desde)
            .addSnapshotListener { snap, _ ->
                registros = snap?.documents?.map { d ->
                    Reg(d.getString("data").orEmpty(), d.getString("turno").orEmpty(), d.getDouble("vazaoLh") ?: 0.0,
                        d.getDouble("conc"), d.getDouble("consumoKg") ?: 0.0)
                }.orEmpty()
                atualizar()
            }
        db.collection("floculante_entradas").orderBy("criadoEm", Query.Direction.DESCENDING).limit(10)
            .addSnapshotListener { snap, _ ->
                val lista = v<LinearLayout>(R.id.listaEntradas); lista.removeAllViews()
                snap?.documents?.forEach { d ->
                    lista.addView(linha("${br(d.getString("data").orEmpty())} · Lote ${d.getString("lote").orEmpty()} · " +
                        "${fmt(d.getDouble("kg") ?: Double.NaN)} kg · Coleta: ${if (d.getBoolean("coleta") == true) "sim" else "não"}"))
                }
            }
    }

    private fun atualizar() {
        val dias = registros.map { it.data }.distinct().size
        val total = registros.sumOf { it.consumoKg }
        val porDia = if (dias > 0) total / dias else Double.NaN
        kpis[0].text = fmt(estoqueKg)
        kpis[1].text = fmt(porDia)
        kpis[2].text = fmt(estoqueKg / porDia, 0)

        // Média simples de cada turno; turno sem valor informado fica NaN e o gráfico mostra "–"
        fun media(campo: (Reg) -> Double?) = TURNOS.map { t -> registros.filter { it.turno == t }.mapNotNull(campo).average() }
        graficos[0].definir(media { it.conc })
        graficos[1].definir(media { it.vazaoLh })
        graficos[2].definir(media { it.consumoKg })

        val lista = v<LinearLayout>(R.id.listaRegistros); lista.removeAllViews()
        registros.sortedWith(compareByDescending<Reg> { it.data }.thenBy { it.turno }).take(15).forEach { r ->
            val conc = if (admin && r.conc != null) " · ${fmt(r.conc, 2)}%" else ""
            lista.addView(linha("${br(r.data)} · Turno ${r.turno} · ${fmt(r.vazaoLh, 0)} L/h$conc · ${fmt(r.consumoKg)} kg"))
        }
    }
}

private const val PIN_ADMIN = "1234" // TROQUE pelo PIN dos administradores
private val TURNOS = listOf("X", "Y", "Z")

// Paleta do app: branco, verde e preto (o verde é o mesmo de @color/verde no tema)
private const val VERDE = 0xFF188038.toInt()
private const val PRETO = 0xFF000000.toInt()
private const val BRANCO = 0xFFFFFFFF.toInt()

/** Fundo dos cartões de indicador e dos gráficos: branco com contorno verde. */
private fun fundoCartao(dp: Float) = GradientDrawable().apply {
    setColor(BRANCO); setStroke(dp.toInt().coerceAtLeast(1), VERDE); cornerRadius = 8 * dp
}

/** Gráfico de barras de uma medida: uma barra por turno, com o valor escrito em cima. */
class GraficoBarras(context: Context, private val titulo: String, private val formato: (Double) -> String) : View(context) {
    private val dp = resources.displayMetrics.density
    private var valores = TURNOS.map { Double.NaN }
    private val barra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VERDE }
    private val eixo = Paint().apply { color = PRETO; strokeWidth = dp }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRETO }
    private val forma = Path()

    init { background = fundoCartao(dp) }

    fun definir(novos: List<Double>) { valores = novos; invalidate() }

    override fun onDraw(c: Canvas) {
        val pad = 12 * dp
        texto.textAlign = Paint.Align.LEFT; texto.textSize = 13 * dp; texto.typeface = Typeface.DEFAULT
        c.drawText(titulo, pad, pad + 12 * dp, texto)

        val topo = pad + 44 * dp          // abaixo do título e do rótulo de valor
        val base = height - pad - 20 * dp // acima das letras dos turnos
        val max = valores.filter { !it.isNaN() }.maxOrNull() ?: 0.0
        val coluna = (width - 2 * pad) / valores.size
        val largura = minOf(coluna * 0.5f, 48 * dp)
        val raio = 4 * dp
        texto.textAlign = Paint.Align.CENTER
        valores.forEachIndexed { i, valor ->
            val cx = pad + coluna * (i + 0.5f)
            val h = if (valor.isNaN() || max <= 0) 0f else ((base - topo) * (valor / max)).toFloat().coerceAtLeast(0f)
            if (h > 0f) {
                forma.rewind() // só os cantos de cima arredondados; a barra nasce reta no eixo
                forma.addRoundRect(cx - largura / 2, base - h, cx + largura / 2, base,
                    floatArrayOf(raio, raio, raio, raio, 0f, 0f, 0f, 0f), Path.Direction.CW)
                c.drawPath(forma, barra)
            }
            texto.textSize = 14 * dp; texto.typeface = Typeface.DEFAULT_BOLD
            c.drawText(if (valor.isNaN()) "–" else formato(valor), cx, base - h - 6 * dp, texto)
            texto.textSize = 13 * dp; texto.typeface = Typeface.DEFAULT
            c.drawText(TURNOS[i], cx, base + 16 * dp, texto)
        }
        c.drawLine(pad, base, width - pad, base, eixo)
    }
}
