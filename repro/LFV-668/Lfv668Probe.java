import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ckb.SoraniAnalyzer;
import org.apache.lucene.analysis.ckb.SoraniNormalizationFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.QueryBuilder;

/** Prints the Sorani normalization of HEH + trailing characters and a 3-document search (public API). */
public class Lfv668Probe {
  static final String HOUSE_HEH = "\u062E\u0627\u0646\u0647";
  static final String HOUSE_AE = "\u062E\u0627\u0646\u06D5";
  static final String IS_BIG = "\u06AF\u06D5\u0648\u0631\u06D5\u06CC\u06D5";

  static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0) b.append(' ');
      b.append(String.format("%04X", (int) s.charAt(i)));
    }
    return b.toString();
  }

  static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add("[" + hex(t.toString()) + "]");
      ts.end();
    }
    return out;
  }

  public static void main(String[] args) throws Exception {
    Analyzer filter =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String f) {
            Tokenizer t = new KeywordTokenizer();
            return new TokenStreamComponents(t, new SoraniNormalizationFilter(t));
          }
        };
    Analyzer std =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String f) {
            Tokenizer t = new StandardTokenizer();
            return new TokenStreamComponents(t, t);
          }
        };
    String[][] tails = {
      {"none", ""}, {"RLM 200F", "\u200F"}, {"LRM 200E", "\u200E"}, {"ALM 061C", "\u061C"},
      {"PDF 202C", "\u202C"}, {"RLO 202E", "\u202E"}, {"PDI 2069", "\u2069"},
      {"RLM RLM", "\u200F\u200F"}, {"ZWNJ 200C", "\u200C"}, {"ZWJ 200D", "\u200D"},
      {"TATWEEL 0640", "\u0640"}, {"FATHA 064E", "\u064E"}, {"RLM ZWJ", "\u200F\u200D"}
    };
    for (String[] tl : tails) {
      System.out.println(
          "FILTER heh+" + tl[0] + " in=[" + hex(HOUSE_HEH + tl[1]) + "] out=" + terms(filter, HOUSE_HEH + tl[1]));
    }
    System.out.println("FILTER leading RLM + REH WAW: out=" + terms(filter, "\u200F\u0631\u0648"));
    System.out.println("STD tokens of HEH+RLM+space+IS_BIG: " + terms(std, HOUSE_HEH + "\u200F " + IS_BIG));
    try (SoraniAnalyzer a = new SoraniAnalyzer()) {
      System.out.println("ANALYZER terms AE      : " + terms(a, HOUSE_AE + " " + IS_BIG));
      System.out.println("ANALYZER terms HEH     : " + terms(a, HOUSE_HEH + " " + IS_BIG));
      System.out.println("ANALYZER terms HEH+RLM : " + terms(a, HOUSE_HEH + "\u200F " + IS_BIG));
      String kurdish = "\u06A9\u0648\u0631\u062F\u06CC\u06CC\u0647";
      System.out.println("ANALYZER stem KWRDYYH     : " + terms(a, kurdish));
      System.out.println("ANALYZER stem KWRDYYH+RLM : " + terms(a, kurdish + "\u200F"));
      System.out.println(
          "NORMALIZE AE=[" + hex(a.normalize("body", HOUSE_AE).utf8ToString())
              + "] HEH+RLM=[" + hex(a.normalize("body", HOUSE_HEH + "\u200F").utf8ToString()) + "]");
      try (Directory dir = new ByteBuffersDirectory()) {
        String[][] docs = {{"ae", HOUSE_AE}, {"heh", HOUSE_HEH}, {"heh+rlm", HOUSE_HEH + "\u200F"}};
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
          for (String[] d : docs) {
            Document doc = new Document();
            doc.add(new StringField("id", d[0], Field.Store.YES));
            doc.add(new TextField("body", d[1] + " " + IS_BIG, Field.Store.NO));
            w.addDocument(doc);
          }
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          StoredFields sf = s.storedFields();
          QueryBuilder qb = new QueryBuilder(a);
          String[][] queries = {{"AE", HOUSE_AE}, {"HEH", HOUSE_HEH}, {"HEH+RLM", HOUSE_HEH + "\u200F"}};
          for (String[] q : queries) {
            Query query = qb.createBooleanQuery("body", q[1]);
            List<String> ids = new ArrayList<>();
            for (ScoreDoc sd : s.search(query, 10).scoreDocs) ids.add(sf.document(sd.doc).get("id"));
            java.util.Collections.sort(ids);
            System.out.println("SEARCH query " + q[0] + " -> " + ids.size() + " of 3 " + ids);
          }
        }
      }
    }
    filter.close();
    std.close();
  }
}
