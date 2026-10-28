package probe;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;

/** Public-API probe: token offsets around the 1024-char unknown-word cap, and the indexed terms. */
public class CapProbe {
  static final String EMOJI = "😀";
  static final String EYE = "👁️";

  static String thai(int pos) {
    String t = "ภาษาไทย".repeat(200);
    return t.substring(0, pos) + EMOJI + t.substring(pos, 1198);
  }

  static String hex(char c) {
    return String.format("U+%04X", (int) c);
  }

  static void dump(String label, TokenStream ts, int maxShown) throws Exception {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
    ts.reset();
    int n = 0, bad = 0;
    StringBuilder sb = new StringBuilder();
    while (ts.incrementToken()) {
      boolean valid = UnicodeUtil.validUTF16String(term);
      if (!valid) bad++;
      boolean near = off.startOffset() >= 1021 && off.startOffset() <= 1025 || term.length() > 2;
      if (near && maxShown-- > 0) {
        sb.append("\n    [").append(off.startOffset()).append(',').append(off.endOffset())
            .append(") len=").append(term.length())
            .append(" first=").append(hex(term.charAt(0)))
            .append(" last=").append(hex(term.charAt(term.length() - 1)))
            .append(valid ? "" : "  NOT VALID UTF-16");
      }
      n++;
    }
    ts.end();
    ts.close();
    System.out.println(label + ": tokens=" + n + " invalid=" + bad + sb);
  }

  static Analyzer tok(JapaneseTokenizer.Mode mode) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String f) {
        Tokenizer t = new JapaneseTokenizer(null, true, mode);
        return new TokenStreamComponents(t);
      }
    };
  }

  static void index(String label, Analyzer a, String text, String probeTerm) throws Exception {
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      Document d = new Document();
      d.add(new TextField("f", text, Field.Store.NO));
      w.addDocument(d);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        TermsEnum te = MultiTerms.getTerms(r, "f").iterator();
        int withEmoji = 0, withFffd = 0;
        StringBuilder sb = new StringBuilder();
        for (BytesRef t = te.next(); t != null; t = te.next()) {
          String u = t.utf8ToString();
          if (u.contains(probeTerm)) withEmoji++;
          int i = u.indexOf('�');
          if (i >= 0) {
            withFffd++;
            sb.append(" [len=").append(u.length()).append(", U+FFFD at ").append(i).append(']');
          }
        }
        int hits = new IndexSearcher(r).count(new TermQuery(new Term("f", probeTerm)));
        System.out.println(label + ": terms containing the emoji=" + withEmoji
            + ", terms containing U+FFFD=" + withFffd + sb + ", TermQuery(emoji) hits=" + hits);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    String s = thai(1023);
    dump("JapaneseTokenizer SEARCH, Thai+emoji@1023", tok(JapaneseTokenizer.Mode.SEARCH).tokenStream("f", s), 4);
    dump("JapaneseTokenizer EXTENDED, Thai+emoji@1023", tok(JapaneseTokenizer.Mode.EXTENDED).tokenStream("f", s), 4);
    dump("JapaneseAnalyzer (default), Thai+emoji@1023", new JapaneseAnalyzer().tokenStream("f", s), 4);
    dump("KoreanAnalyzer (default), 400 x (U+1F441 U+FE0F)", new KoreanAnalyzer().tokenStream("f", EYE.repeat(400)), 4);
    for (int pos : new int[] {1022, 1023, 1024}) {
      index("index SEARCH, emoji@" + pos, tok(JapaneseTokenizer.Mode.SEARCH), thai(pos), EMOJI);
      index("index EXTENDED, emoji@" + pos, tok(JapaneseTokenizer.Mode.EXTENDED), thai(pos), EMOJI);
    }
  }
}
