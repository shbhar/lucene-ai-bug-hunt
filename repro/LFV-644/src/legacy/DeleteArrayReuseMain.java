package legacy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Same scenario as the JUnit, for releases without the 9.x test framework. Exits 1 on the bug. */
public class DeleteArrayReuseMain {
  static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  static int count(DirectoryReader r, String id) throws Exception {
    return new IndexSearcher(r).count(new TermQuery(new Term("id", id)));
  }

  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory("delarr");
    int b1, b2, keep, delMe, keep2;
    try (Directory dir = FSDirectory.open(tmp)) {
      IndexWriterConfig cfg = new IndexWriterConfig();
      cfg.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, cfg)) {
        w.addDocument(doc("buffered1"));
        w.addDocument(doc("buffered2"));
        w.addDocument(doc("keep"));
        Term[] batch = {new Term("id", "buffered1")};
        w.deleteDocuments(batch);
        batch[0] = new Term("id", "buffered2");
        w.deleteDocuments(batch);
        w.addDocument(doc("delete-me"));
        w.addDocument(doc("keep2"));
        Query[] queries = {new TermQuery(new Term("id", "delete-me"))};
        w.deleteDocuments(queries);
        queries[0] = new TermQuery(new Term("id", "keep2"));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        b1 = count(r, "buffered1"); b2 = count(r, "buffered2"); keep = count(r, "keep");
        delMe = count(r, "delete-me"); keep2 = count(r, "keep2");
      }
    }
    System.out.println("live counts: buffered1=" + b1 + " buffered2=" + b2 + " keep=" + keep
        + " delete-me=" + delMe + " keep2=" + keep2 + " (expected 0 0 1 0 1)");
    if (b1 != 0 || b2 != 0 || keep != 1 || delMe != 0 || keep2 != 1) {
      System.out.println("BUG: deletes were resolved from the caller's array after the call returned");
      System.exit(1);
    }
  }
}
