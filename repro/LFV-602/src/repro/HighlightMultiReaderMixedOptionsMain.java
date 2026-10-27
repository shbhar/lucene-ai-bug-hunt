package repro;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * Version-portable probe (8.x .. main): UnifiedHighlighter over a MultiReader whose two indexes
 * index a field with different options. Exits 1 if any case throws or misses a highlight.
 */
public class HighlightMultiReaderMixedOptionsMain {
  static final Analyzer A = new StandardAnalyzer();

  static FieldType body(boolean norms, boolean offsets) {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setOmitNorms(!norms);
    if (offsets) ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  static Directory index(FieldType bt, String text, Field extra) throws Exception {
    Directory d = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(d, new IndexWriterConfig(A));
    Document doc = new Document();
    doc.add(new Field("body", text, bt));
    if (extra != null) doc.add(extra);
    w.addDocument(doc);
    w.close();
    return d;
  }

  static String run(String name, Directory d1, Directory d2) throws Exception {
    DirectoryReader r1 = DirectoryReader.open(d1);
    DirectoryReader r2 = DirectoryReader.open(d2);
    MultiReader mr = new MultiReader(new IndexReader[] {r1, r2}, true);
    try {
      IndexSearcher s = new IndexSearcher(mr);
      Query q = new TermQuery(new Term("body", "alpha"));
      TopDocs td = s.search(q, 10);
      if (td.scoreDocs.length != 2) return name + ": FAIL search hits=" + td.scoreDocs.length;
      String[] snips;
      try {
        snips = new UnifiedHighlighter(s, A).highlight("body", q, td);
      } catch (RuntimeException e) {
        return name + ": FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      int ok = 0;
      for (String sn : snips) if (sn != null && sn.contains("<b>alpha</b>")) ok++;
      return name + (ok == 2 ? ": ok " : ": FAIL ") + java.util.Arrays.toString(snips);
    } finally {
      mr.close();
    }
  }

  public static void main(String[] args) throws Exception {
    String[] res = {
      run("control-consistent", index(body(true, false), "alpha beta", null), index(body(true, false), "alpha gamma", null)),
      run("norms-differ-only", index(body(true, false), "alpha beta", null), index(body(false, false), "alpha gamma", null)),
      run("offsets-in-one-index", index(body(true, true), "alpha beta", null), index(body(true, false), "alpha gamma", null)),
      run(
          "unrelated-field-differs",
          index(body(true, false), "alpha beta", new StringField("tag", "x", Field.Store.NO)),
          index(body(true, false), "alpha gamma", new SortedDocValuesField("tag", new BytesRef("x"))))
    };
    boolean fail = false;
    for (String r : res) {
      System.out.println(r);
      if (r.contains(": FAIL")) fail = true;
    }
    if (fail) System.exit(1);
  }
}
