// iOS için şablon kataloğunun dışa aktarımı: Android'deki Kotlin şablon tanımları (Templates*.kt) çalıştırılır ve
// çizgiler bit düzeyinde (float) aynı kalacak biçimde shape_templates.bin dosyasına yazılır. Kullanım:
//   cd android && ./gradlew :core:content:compileKotlin
//   javac -cp <classes>:<kotlin-stdlib> ExportShapeTemplates.java && java -cp ... ExportShapeTemplates <çıktı>
import com.karapp.drawguidance.content.shapes.*;
import com.karapp.drawguidance.content.vector.P;
import java.io.*;
import java.util.*;

public class ExportShapeTemplates {
  static void write(DataOutputStream d, List<ShapeTemplate> list) throws IOException {
    d.writeInt(list.size());
    for (ShapeTemplate t : list) {
      d.writeUTF(t.getKey()); d.writeUTF(t.getName()); d.writeUTF(t.getCategory()); d.writeUTF(t.getGroup()); d.writeUTF(t.getDescription());
      d.writeBoolean(t.getSolid() != null); if (t.getSolid() != null) d.writeUTF(t.getSolid());
      d.writeInt(t.getStrokes().size());
      for (List<P> s : t.getStrokes()) { d.writeInt(s.size()); for (P p : s) { d.writeFloat(p.getX()); d.writeFloat(p.getY()); } }
    }
  }
  public static void main(String[] a) throws Exception {
    List<ShapeTemplate> modeOnly = new ArrayList<>();
    for (ShapeTemplate t : ShapeTemplates.INSTANCE.getModeOnly()) if (!LibraryTemplates.INSTANCE.isLibraryKey(t.getKey())) modeOnly.add(t);
    try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(a[0])))) {
      d.writeInt(0x53485450); d.writeInt(ShapeTemplates.VERSION);
      write(d, ShapeTemplates.INSTANCE.getAll());
      write(d, modeOnly);
    }
    System.out.println("all=" + ShapeTemplates.INSTANCE.getAll().size() + " modeOnly=" + modeOnly.size());
  }
}
