import com.karapp.drawguidance.content.catalog.CatalogBinary
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.library.ShapeLibrary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** iOS uygulamasına gömülen içerik dosyalarını üretir: `gradle run --args=<çıktı klasörü>`. */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "out" }).apply { mkdirs() }
    val vectors = CatalogCodec.bundledVectors()
    File(out, "catalog-vectors.bin").writeBytes(CatalogBinary.encodeVectors(CatalogInfo.VERSION, vectors))
    File(out, "catalog-nets.bin").writeBytes(CatalogBinary.encodeModeNets(CatalogInfo.VERSION, CatalogCodec.bundledModeNets()))
    // Kategoriler (sıra, kitle, bölüm, seçicide görünürlük): vektör kaydı `listed` bilgisini taşımaz
    val cats = JsonArray(ShapeLibrary.bundledCategories.map { c ->
        buildJsonObject {
            put("id", c.id); put("title", c.title); put("audience", c.audience.name); put("section", c.section); put("listed", c.listed)
            put("count", c.shapes.size)
        }
    })
    File(out, "catalog-categories.json").writeText(cats.toString())
    println("vektör ${vectors.size}, kategori ${ShapeLibrary.bundledCategories.size}")
}
