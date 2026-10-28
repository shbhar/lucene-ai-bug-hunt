/*
 * Version-portable repro for "telugu-composed-au-not-folded-to-oo".
 *
 * Public API only, present unchanged from Lucene 8.10.0 to main: TeluguNormalizationFilter,
 * WhitespaceTokenizer, CustomAnalyzer (SPI names "standard" + "teluguNormalization"),
 * TeluguAnalyzer. No test-framework, so it compiles on 8.x, 9.0.0 and later alike.
 *
 * Exit codes: 1 = bug reproduces (AssertionError); 0 = both spellings of AU normalize alike;
 * 2 = control failed (precomposed AU is not folded to OO on this version, so the claim does not
 * apply as stated). On a version without the Telugu classes (8.9.0 and older) it does not compile.
 */
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.te.TeluguAnalyzer;
import org.apache.lucene.analysis.te.TeluguNormalizationFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.BytesRef;

public class TeluguAuHistoryRepro {

  static final String PRECOMPOSED_AU = "ఔ"; // AU
  static final String DECOMPOSED_AU = "ఒౌ"; // O + AU length mark
  static final String OO = "ఓ"; // OO
  static final String WORD_PRE = "ఔషధం"; // AU-SSA-DHA-ANUSVARA
  static final String WORD_DEC = "ఒౌషధం"; // same word, decomposed AU

  static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0) b.append(' ');
      b.append(String.format("U+%04X", (int) s.charAt(i)));
    }
    return b.toString();
  }

  static List<String> terms(TokenStream ts) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) out.add(t.toString());
    ts.end();
    ts.close();
    return out;
  }

  static String filterOnly(String s) throws IOException {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(s));
    List<String> l = terms(new TeluguNormalizationFilter(tok));
    if (l.size() != 1) throw new IllegalStateException("expected one token, got " + l);
    return l.get(0);
  }

  static String analyze(Analyzer a, String s) throws IOException {
    List<String> l = terms(a.tokenStream("f", s));
    if (l.size() != 1) throw new IllegalStateException("expected one token, got " + l);
    return l.get(0);
  }

  public static void main(String[] args) throws Exception {
    // Control: the class's own AU -> OO fold (pinned by upstream TestTeluguNormalizer.testVowels).
    String pre = filterOnly(PRECOMPOSED_AU);
    System.out.println("filter(precomposed AU " + hex(PRECOMPOSED_AU) + ") = " + hex(pre));
    if (!pre.equals(OO)) {
      System.out.println("RESULT control_failed (precomposed AU not folded to OO)");
      System.exit(2);
    }

    // Claim 1: the decomposed spelling must normalize like the precomposed one.
    String dec = filterOnly(DECOMPOSED_AU);
    System.out.println("filter(decomposed AU " + hex(DECOMPOSED_AU) + ") = " + hex(dec));

    // Claim 2: the factory javadoc's example chain (standard tokenizer + teluguNormalization).
    String chainPre;
    String chainDec;
    try (Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("standard")
            .addTokenFilter("teluguNormalization")
            .build()) {
      chainPre = analyze(a, WORD_PRE);
      chainDec = analyze(a, WORD_DEC);
    }
    System.out.println("chain(standard+teluguNormalization)(" + hex(WORD_PRE) + ") = " + hex(chainPre));
    System.out.println("chain(standard+teluguNormalization)(" + hex(WORD_DEC) + ") = " + hex(chainDec));

    // Informational only (not asserted): stock TeluguAnalyzer, index-time and normalize().
    try (Analyzer ta = new TeluguAnalyzer()) {
      String tPre = analyze(ta, WORD_PRE);
      String tDec = analyze(ta, WORD_DEC);
      BytesRef nPre = ta.normalize("f", WORD_PRE);
      BytesRef nDec = ta.normalize("f", WORD_DEC);
      System.out.println(
          "INFO TeluguAnalyzer tokenStream equal=" + tPre.equals(tDec)
              + " pre=" + hex(tPre) + " dec=" + hex(tDec));
      System.out.println(
          "INFO TeluguAnalyzer normalize equal=" + nPre.equals(nDec)
              + " pre=" + hex(nPre.utf8ToString()) + " dec=" + hex(nDec.utf8ToString()));
    }

    boolean bug1 = !dec.equals(pre);
    boolean bug2 = !chainDec.equals(chainPre);
    System.out.println("RESULT filter_mismatch=" + bug1 + " chain_mismatch=" + bug2);
    if (bug1 || bug2) {
      throw new AssertionError(
          "decomposed AU normalizes differently from precomposed AU: filter "
              + hex(dec) + " vs " + hex(pre) + "; chain " + hex(chainDec) + " vs " + hex(chainPre));
    }
    System.out.println("RESULT no_bug");
  }
}
