package org.apache.lucene.analysis.sr;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Fact-check probe for LFV-534: prints observations; asserts nothing so every line is recorded. */
public class TestFactCheckSerbian534 extends LuceneTestCase {

  private static List<String> tokens(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
    }
    return out;
  }

  public void testObserve() throws Exception {
    try (Analyzer a = new SerbianAnalyzer();
        Directory dir = newDirectory()) {
      System.out.println("FC tokens(Београд Čačak)=" + tokens(a, "Београд Čačak"));
      for (String w : new String[] {"поверљиво", "poverljivo", "povérljivo", "šifra", "шифра", "sifra", "Čača", "Đak", "djak", "zak"}) {
        System.out.println("FC tokens(" + w + ")=" + tokens(a, w) + " normalize=" + a.normalize("body", w).utf8ToString());
      }
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "Београд Čačak", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser classic = new QueryParser("body", a);
        StandardQueryParser flex = new StandardQueryParser(a);
        SimpleQueryParser simple = new SimpleQueryParser(a, "body");
        String[] qs = {"Beogr*", "Cac*", "Беогр*", "Čač*", "Београд~1", "Бео?рад", "/бео.*/", "Београд", "\"Београд Čačak\"", "Čača*", "[a TO š]"};
        for (String q : qs) {
          Query c = classic.parse(q);
          Query f = flex.parse(q, "body");
          System.out.println("FC classic " + q + " -> " + c + " hits=" + s.count(c) + " | flexible -> " + f + " hits=" + s.count(f));
        }
        for (String q : new String[] {"Beogr*", "Беогр*", "Čač*", "Београд~1"}) {
          Query sq = simple.parse(q);
          System.out.println("FC simple " + q + " -> " + sq + " hits=" + s.count(sq));
        }
      }
    }
  }
}
