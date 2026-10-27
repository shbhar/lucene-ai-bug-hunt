package probe;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent audit probe (outside the ICU package): a document made of two short lines followed
 * by one long line of Japanese text without spaces; a supplementary ideograph is placed at offsets
 * relative to the last line break. Each document is searched by TermQuery for that ideograph.
 */
public class TestAuditIcuChunkEdgeSearch extends LuceneTestCase {

  static String doc(int posAfterBreak, String probe) {
    StringBuilder sb = new StringBuilder("見出し 一行目\n二行目\n");
    int base = sb.length(); // the first chunk's last whitespace is at base-1
    while (sb.length() < base + posAfterBreak) sb.append("日本語の文章");
    sb.setLength(base + posAfterBreak);
    sb.append(probe);
    while (sb.length() < base + 6000) sb.append("日本語の文章");
    return sb.toString();
  }

  public void testTermQueryAroundChunkEdge() throws Exception {
    String probe = new String(Character.toChars(0x2000B)); // CJK Extension B ideograph
    int[] positions = {100, 4094, 4095, 4096, 4097};
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String f) {
            Tokenizer t = new ICUTokenizer();
            return new TokenStreamComponents(t);
          }
        };
    List<String> missed = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (int p : positions) {
          Document d = new Document();
          d.add(new StringField("id", Integer.toString(p), Field.Store.YES));
          d.add(new TextField("body", doc(p, probe), Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        for (int p : positions) {
          var q =
              new org.apache.lucene.search.BooleanQuery.Builder()
                  .add(new TermQuery(new Term("body", probe)), org.apache.lucene.search.BooleanClause.Occur.MUST)
                  .add(new TermQuery(new Term("id", Integer.toString(p))), org.apache.lucene.search.BooleanClause.Occur.FILTER)
                  .build();
          int c = s.count(q);
          System.out.println("probe at " + p + " chars after the last line break: hits=" + c);
          if (c != 1) missed.add(Integer.toString(p));
        }
      }
    }
    assertTrue("documents missed by TermQuery(U+2000B): " + missed, missed.isEmpty());
  }
}
