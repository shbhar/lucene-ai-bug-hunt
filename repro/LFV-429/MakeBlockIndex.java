import java.nio.file.*;
import java.util.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Version;

/** Writes an index of parent/child blocks (IndexWriter#addDocuments, no parent field) in 3 segments. */
public class MakeBlockIndex {
  public static void main(String[] args) throws Exception {
    Path p = Paths.get(args[0]);
    try (Directory dir = FSDirectory.open(p)) {
      IndexWriterConfig c = new IndexWriterConfig();
      c.setMergePolicy(NoMergePolicy.INSTANCE);
      c.setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, c)) {
        Random r = new Random(42);
        int b = 0;
        for (int seg = 0; seg < 3; seg++) {
          for (int i = 0; i < 40; i++, b++) {
            List<Document> block = new ArrayList<>();
            int n = 1 + r.nextInt(3);
            for (int j = 0; j < n; j++) {
              Document d = new Document();
              d.add(new StringField("id", "b" + b + "c" + j, Field.Store.YES));
              d.add(new StringField("type", "child", Field.Store.NO));
              d.add(new StringField("blk", "b" + b, Field.Store.YES));
              d.add(new TextField("body", "w" + r.nextInt(5) + " w" + r.nextInt(5) + " t" + (b % 7), Field.Store.NO));
              block.add(d);
            }
            Document parent = new Document();
            parent.add(new StringField("id", "b" + b, Field.Store.YES));
            parent.add(new StringField("type", "parent", Field.Store.NO));
            parent.add(new StringField("blk", "b" + b, Field.Store.YES));
            parent.add(new TextField("body", "p" + r.nextInt(5) + " t" + (b % 7), Field.Store.NO));
            block.add(parent);
            w.addDocuments(block);
          }
          w.commit();
        }
      }
    }
    System.out.println("wrote block index with Lucene " + Version.LATEST);
  }
}
