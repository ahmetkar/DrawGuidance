import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.*
import com.karapp.drawguidance.data.repository.encodeStrokes
import java.io.File

fun main(args: Array<String>) {
    val out = File(args[0]); out.mkdirs()
    val layers = listOf(Layer.Default, Layer("l2", "Üst katman", visible = false, locked = true))
    val plainPage = listOf(
        Stroke("8f14e45f-ceea-467f-a8f4-5d4b8c1e0a11", listOf(StrokePoint(10f, 20.5f), StrokePoint(-3.25f, 1000.01f), StrokePoint(12.5f, 7f)), "#1E88E5", BrushType.PENCIL, 4f, 1f, 0),
        Stroke("custom-id", listOf(StrokePoint(0.123456f, 9.87654f), StrokePoint(1e8f, -0.001f)), "#80FF0000", BrushType.MARKER, 12.5f, 0.6f, 1, groupId = "g1", flow = 0.4f, strength = 0.7f, layerId = "l2"),
        Stroke("a2b3c4d5-0000-4000-8000-00000000abcd", listOf(StrokePoint(1f, 2f, pressure = 0.5f, tilt = 0.25f), StrokePoint(3f, 4f, pressure = 0.75f, tilt = 0.1f, orient = 0.3f)), "#000000", BrushType.CALLIGRAPHY, 6f, 1f, -5,
            clip = listOf(StrokePoint(0f, 0f), StrokePoint(5f, 0f), StrokePoint(5f, 5f), StrokePoint(0f, 5f))),
        Stroke("t1", listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f), StrokePoint(100f, 40f), StrokePoint(0f, 40f)), "#333333", BrushType.TEXT, 24f, 1f, 3,
            text = TextSpec("Merhaba ğüşİ 😀\n• madde", TextFont.CAVEAT, bold = true, underline = true, align = TextAlignKind.CENTER, paragraph = TextParagraph.BULLET, outlineColorHex = "#FFFFFF", outlineWidth = 1.5f)),
        Stroke("f1", listOf(StrokePoint(0f, 0f), StrokePoint(10f, 0f), StrokePoint(10f, 10f), StrokePoint(0f, 10f)), "#222222", BrushType.FORMULA, 18f, 1f, 4, formula = FormulaSpec("x^2+\\slot{a}{3}", "quad")),
    )
    val page2 = listOf(Stroke("e1", listOf(StrokePoint(5f, 5f)), "#FFFFFF", BrushType.ERASER, 30f, 1f, 0))
    File(out, "plain.bin").writeBytes(ContentCodec.encodePages(listOf(plainPage, page2), layers = layers))
    File(out, "plain.json").writeText(encodeStrokes(plainPage + page2))

    val full = listOf(
        Stroke("s1", listOf(StrokePoint(0f, 0f), StrokePoint(50f, 0f), StrokePoint(50f, 50f), StrokePoint(10f, 10f)), "#1E1E1E", BrushType.SHAPE, 4f, 1f, 0,
            shape = ShapeSpec("graph_fn", listOf(SubPath(3, closed = true, fillColorHex = "#FFEEAA", nodes = listOf(0, 2)), SubPath(1, label = "x", labelSize = 12f)),
                ShapeStyle(strokeWidth = 2.5f, fillColorHex = "#ABCDEF", fillGradient = GradientSpec(listOf("#FF0000", "#0000FF"), angle = 45f)),
                graph = GraphSpec(formula = "sin(x)", values = listOf(1f, 2.5f), valueLabels = mapOf(0 to "a", 3 to "b"), tickLabels = mapOf("x:3" to "7"), angleDeg = true),
                panel = PanelRef("set1", 2, "H(1,2)"), params = mapOf("v" to "20"))),
        Stroke("d1", listOf(StrokePoint(0f, 0f), StrokePoint(200f, 0f), StrokePoint(200f, 100f), StrokePoint(0f, 100f)), "#000000", BrushType.DIAGRAM, 4f, 1f, 1,
            diagram = DiagramSpec(DiagramKind.FLOW, listOf(DiagramNode("n1", "Başla", 0.1f, 0.1f, 0.2f, 0.1f, role = "focal"), DiagramNode("n2", "Bitir", 0.6f, 0.6f, 0.2f, 0.1f, DiagramBox.DIAMOND)),
                listOf(DiagramEdge("n1", "n2", "evet", fromSide = DiagramSide.RIGHT, toSide = DiagramSide.TOP)), frame = DiagramFrame(-0.1f, 0f, 1.2f, 1f), theme = "ocean")),
        Stroke("b1", listOf(StrokePoint(0f, 0f), StrokePoint(80f, 0f), StrokePoint(80f, 60f), StrokePoint(0f, 60f)), "#000000", BrushType.TEXT, 20f, 1f, 2, hidden = true,
            text = TextSpec("Bağır!", TextFont.BANGERS, box = TextBox(width = 80f, fillHex = "#FFFFFF", balloon = BalloonSpec(BalloonStyle.SHOUT, listOf(BalloonTail(0.3f, 1.4f, 0.1f))), effects = TextEffects(shadowHex = "#000000", caps = true)),
                path = TextPathSpec(listOf(StrokePoint(0f, 30f), StrokePoint(80f, 30f)), 80f, 60f, start = 0.2f, below = true))),
        Stroke("p1", listOf(StrokePoint(1f, 1f), StrokePoint(2f, 3f)), "#8E24AA", BrushType.BRUSH_3D, 10f, 1f, 3,
            params = BrushParams(spacing = 0.2f, nibFollow = true, tube = TubeParams(material = TubeMaterial.METAL), depth3d = Depth3D(Plane3D.TOP, 0.6f, ex = 0.5f, ey = -0.5f),
                comicLines = ComicLinesParams("speed", 0.5f, 42, listOf(0f, 0f, 10f, 10f), listOf(1f, 1f, 9f, 9f), palette = true)),
            space = Space3D(FloatArray(12) { it * 1.5f - 3f }, floatArrayOf(0.5f, -1.25f, 3f, 4.0625f), 1234567890123L, 7)),
        Stroke("i1", listOf(StrokePoint(0f, 0f), StrokePoint(64f, 0f), StrokePoint(64f, 64f), StrokePoint(0f, 64f)), "#000000", BrushType.IMAGE, 1f, 1f, 4, assetId = "11111111-2222-4333-8444-555555555555",
            model = Model3DLink("m1", "asset-9")),
    )
    File(out, "full.bin").writeBytes(ContentCodec.encodePages(listOf(full), layers = listOf(Layer.Default)))
    File(out, "full.json").writeText(encodeStrokes(full))
    val doc = ContentCodec.decodeDocument(File(out, "full.bin").readBytes())
    val sp = doc.pages[0][3].space!!
    File(out, "full-space.txt").writeText(sp.frame.joinToString(",") + "|" + sp.local.joinToString(",") + "|" + sp.stamp + "|" + sp.view + "|" + doc.pages[0][4].model)
    // Swift'in ürettiği dosyalar varsa okunabildiğini doğrula
    (out.listFiles() ?: emptyArray<File>()).filter { it.name.startsWith("swift-") && it.name.endsWith(".bin") }.forEach { f: File ->
        val d = ContentCodec.decodeDocument(f.readBytes())
        println("${f.name}: ${d.pages.size} sayfa, ${d.pages.sumOf { it.size }} eleman, katman ${d.layers.size}")
        File(out, f.name.removeSuffix(".bin") + ".kotlin.json").writeText(encodeStrokes(d.pages.flatten()))
    }
    println("ok")
}
