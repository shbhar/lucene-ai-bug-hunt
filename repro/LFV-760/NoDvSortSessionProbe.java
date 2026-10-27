import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * One writer session, step by step: index sort on "sort" (STRING); doc 2 carries "sort" only as a
 * StringField; doc 3 carries it correctly as a SortedDocValuesField. Then a second writer session.
 * Public API only, core jar only.
 */
public class NoDvSortSessionProbe {

  static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  static void add(IndexWriter w, Document d, String what) {
    try {
      w.addDocument(d);
      System.out.println(what + ": addDocument accepted");
    } catch (Exception e) {
      System.out.println(what + ": addDocument threw " + e);
    }
  }

  static void commit(IndexWriter w, String what) {
    try {
      w.commit();
      System.out.println(what + ": commit ok");
    } catch (Exception e) {
      System.out.println(what + ": commit threw " + e);
    }
  }

  static void state(IndexWriter w, Directory dir) throws Exception {
    StringBuilder ids = new StringBuilder();
    int n;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      n = r.numDocs();
      StoredFields sf = r.storedFields();
      for (int i = 0; i < r.maxDoc(); i++) {
        ids.append(ids.length() == 0 ? "" : ",").append(sf.document(i).get("id"));
      }
    }
    System.out.println(
        "  writer open="
            + w.isOpen()
            + " tragic="
            + (w.getTragicException() != null)
            + " | committed index: "
            + n
            + " doc(s), ids ["
            + ids
            + "]");
  }

  public static void main(String[] args) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    Sort sort = new Sort(new SortField("sort", SortField.Type.STRING));

    System.out.println("session 1: IndexWriterConfig.setIndexSort(" + sort + ")");
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(sort));
    add(w, doc("0"), "doc 0 (no sort field)");
    commit(w, "doc 0");
    add(w, doc("1"), "doc 1 (no sort field)");
    Document d2 = doc("2");
    d2.add(new StringField("sort", "b", Field.Store.NO));
    add(w, d2, "doc 2 (sort only as StringField, no doc values)");
    Document d3 = doc("3");
    d3.add(new SortedDocValuesField("sort", new BytesRef("a")));
    add(w, d3, "doc 3 (sort as SortedDocValuesField, correct)");
    commit(w, "docs 1-3");
    state(w, dir);
    if (w.isOpen()) {
      w.close();
    }

    System.out.println("session 2: new IndexWriter on the same directory and index sort");
    IndexWriter w2 = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(sort));
    Document d4 = doc("4");
    d4.add(new SortedDocValuesField("sort", new BytesRef("c")));
    add(w2, d4, "doc 4 (sort as SortedDocValuesField, correct)");
    commit(w2, "doc 4");
    state(w2, dir);
    w2.close();
  }
}
