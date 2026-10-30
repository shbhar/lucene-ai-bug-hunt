import java.nio.file.Paths;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/** Run with the Lucene 9.8.0 release jars: 3 blocks (child cN, parent pN) in 2 segments. */
public class MakePre99BlockIndex {
  public static void main(String[] args) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = FSDirectory.open(Paths.get(args[0]));
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int b = 0; b < 3; b++) {
        Document child = new Document();
        child.add(new StoredField("id", "c" + b));
        child.add(new StringField("type", "child", Field.Store.NO));
        Document parent = new Document();
        parent.add(new StoredField("id", "p" + b));
        parent.add(new StringField("type", "parent", Field.Store.NO));
        w.addDocuments(List.of(child, parent));
        if (b == 1) {
          w.commit(); // segment _0: blocks 0 and 1; segment _1 (flushed on close): block 2
        }
      }
    }
    if (Version.LATEST.major != 9 || Version.LATEST.minor >= 9) {
      throw new AssertionError("must be written by a release before 9.9, got " + Version.LATEST);
    }
    System.out.println("wrote 3 blocks with Lucene " + Version.LATEST);
  }
}
