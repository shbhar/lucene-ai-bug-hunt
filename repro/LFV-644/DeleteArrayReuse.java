import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Deletes d0..d5 in three batches of two ids through IndexWriter.deleteDocuments(Term...), with
 * the default IndexWriterConfig; only "keep" should be left. Exits 1 if a run disagrees.
 */
public class DeleteArrayReuse {
  static final String[] IDS = {"d0", "d1", "d2", "d3", "d4", "d5", "keep"};

  public static void main(String[] args) throws Exception {
    List<String> a = batches(true, false);
    List<String> b = batches(false, false);
    List<String> c = batches(true, true);
    List<String> d = reassignedQuerySlot();
    System.out.println("one Term[2] refilled per batch, docs buffered : live = " + a);
    System.out.println("a new Term[2] per batch,       docs buffered : live = " + b);
    System.out.println("one Term[2] refilled per batch, docs flushed  : live = " + c);
    System.out.println("Query[]{id:delete-me}, then slot = id:keep    : live = " + d);
    List<String> keep = List.of("keep");
    boolean ok = a.equals(keep) && b.equals(keep) && c.equals(keep) && d.equals(keep);
    System.out.println(ok ? "OK: every run left [keep]" : "BUG: expected [keep] in every run");
    System.exit(ok ? 0 : 1);
  }

  static List<String> batches(boolean reuseArray, boolean flushFirst) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String id : IDS) {
          w.addDocument(doc(id));
        }
        if (flushFirst) {
          w.flush();
        }
        Term[] batch = new Term[2];
        for (int i = 0; i < 3; i++) {
          if (reuseArray == false) {
            batch = new Term[2];
          }
          batch[0] = new Term("id", "d" + (2 * i));
          batch[1] = new Term("id", "d" + (2 * i + 1));
          w.deleteDocuments(batch);
        }
        w.commit();
      }
      return live(dir);
    }
  }

  static List<String> reassignedQuerySlot() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("delete-me"));
        w.addDocument(doc("keep"));
        Query[] queries = {new TermQuery(new Term("id", "delete-me"))};
        w.deleteDocuments(queries);
        queries[0] = new TermQuery(new Term("id", "keep")); // no further delete call
        w.commit();
      }
      List<String> live = new ArrayList<>();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        for (String id : new String[] {"delete-me", "keep"}) {
          if (s.count(new TermQuery(new Term("id", id))) > 0) {
            live.add(id);
          }
        }
      }
      return live;
    }
  }

  static List<String> live(Directory dir) throws Exception {
    List<String> live = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      for (String id : IDS) {
        if (s.count(new TermQuery(new Term("id", id))) > 0) {
          live.add(id);
        }
      }
    }
    return live;
  }

  static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }
}
