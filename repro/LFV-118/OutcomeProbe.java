import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;

/** Prints accepted/rejected (with the message) for a few two-document cases on field "id". */
public class OutcomeProbe {
  static Field s(String v) { return new StringField("id", v, Field.Store.NO); }
  static Field dv(String v) { return new SortedDocValuesField("id", new BytesRef(v)); }

  static void run(String label, List<IndexableField> first, List<IndexableField> second, boolean flush)
      throws Exception {
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(first);
      if (flush) w.flush();
      String out;
      try {
        w.addDocument(second);
        out = "ACCEPTED";
      } catch (IllegalArgumentException e) {
        out = "REJECTED: " + e.getMessage();
      }
      System.out.println(label + " -> " + out);
    }
  }

  public static void main(String[] args) throws Exception {
    FieldType unfrozen = new FieldType(StringField.TYPE_NOT_STORED);
    run("A doc0=[StringField, SortedDV]  doc1=[StringField]", List.of(s("a"), dv("a")), List.of(s("b")), false);
    run("B doc0=[SortedDV, StringField]  doc1=[StringField]", List.of(dv("a"), s("a")), List.of(s("b")), false);
    run("C doc0=[SortedDV, StringField]  doc1=[SortedDV]", List.of(dv("a"), s("a")), List.of(dv("b")), false);
    run("D doc0=[StringField, SortedDV]  flush  doc1=[StringField]", List.of(s("a"), dv("a")), List.of(s("b")), true);
    run("E doc0=[unfrozen copy of StringField type, SortedDV]  doc1=[same unfrozen]",
        List.of(new Field("id", "a", unfrozen), dv("a")), List.of(new Field("id", "b", unfrozen)), false);
    run("F doc0=[StringField, SortedDV]  doc1=[StringField, StoredField]",
        List.of(s("a"), dv("a")), List.of(s("b"), new StoredField("id", "b")), false);
  }
}
