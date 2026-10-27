import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/** ParallelLeafReader: the first reader that has a field owns it. Do term vectors follow that? */
public class PlrTermVectorsOwner {
  static final FieldType TV = new FieldType(TextField.TYPE_NOT_STORED);

  static {
    TV.setStoreTermVectors(true);
    TV.freeze();
  }

  /** One segment, one document; each pair is a field name and a value, "+tv" = with term vectors. */
  static LeafReader index(String... nameValue) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (int i = 0; i < nameValue.length; i += 2) {
        boolean tv = nameValue[i].endsWith("+tv");
        String name = nameValue[i].replace("+tv", "");
        doc.add(new Field(name, nameValue[i + 1], tv ? TV : TextField.TYPE_NOT_STORED));
      }
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir).leaves().get(0).reader();
  }

  static String first(Terms terms) throws IOException {
    BytesRef t = terms == null ? null : terms.iterator().next();
    return t == null ? null : t.utf8ToString();
  }

  static String vector(LeafReader r, String field) throws IOException {
    Fields f = r.termVectors().get(0);
    return f == null ? null : first(f.terms(field));
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Lucene " + Version.LATEST);

    // 1. The owner of "body" (reader 1) has no term vectors for it; reader 2 does.
    LeafReader r1 = index("body", "first", "title+tv", "heading");
    LeafReader r2 = index("body+tv", "second");
    ParallelLeafReader plr = new ParallelLeafReader(false, r1, r2);
    System.out.println(
        "1) FieldInfo(body).hasTermVectors=" + plr.getFieldInfos().fieldInfo("body").hasTermVectors()
            + "  terms(body)=" + first(plr.terms("body"))
            + "  termVectors(body)=" + vector(plr, "body"));

    // 2. Copy the parallel reader into a new index, then run CheckIndex at levels 1 and 2.
    Directory copy = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(copy, new IndexWriterConfig())) {
      w.addIndexes(SlowCodecReaderWrapper.wrap(plr));
    }
    try (DirectoryReader c = DirectoryReader.open(copy)) {
      LeafReader leaf = c.leaves().get(0).reader();
      System.out.println(
          "2) copy: FieldInfo(body).hasTermVectors="
              + leaf.getFieldInfos().fieldInfo("body").hasTermVectors()
              + "  termVectors(body)=" + vector(leaf, "body"));
    }
    for (int level = 1; level <= 2; level++) {
      try (CheckIndex ci = new CheckIndex(copy)) {
        ci.setLevel(level);
        System.out.println("   CheckIndex level " + level + ": clean=" + ci.checkIndex().clean);
      }
    }

    // 3. Both readers have term vectors for "body": the first reader should win, in either order.
    int wrong = 0;
    for (int i = 0; i < 4; i++) {
      LeafReader a = index("body+tv", "alpha");
      LeafReader b = index("body+tv", "beta");
      if (!"alpha".equals(vector(new ParallelLeafReader(false, a, b), "body"))) wrong++;
      if (!"beta".equals(vector(new ParallelLeafReader(false, b, a), "body"))) wrong++;
    }
    System.out.println("3) " + wrong + " of 8 readers returned the second reader's term vector");
  }
}
