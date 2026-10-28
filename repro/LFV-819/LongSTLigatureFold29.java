package h306hist;

import java.io.StringReader;
import org.apache.lucene.analysis.ASCIIFoldingFilter;
import org.apache.lucene.analysis.ISOLatin1AccentFilter;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * History repro for Lucene 2.9.x - 3.6.x (ASCIIFoldingFilter and ISOLatin1AccentFilter in core
 * org.apache.lucene.analysis, TermAttribute API). Public API only. Exits non-zero (AssertionError)
 * when ASCIIFoldingFilter leaves U+FB05 LATIN SMALL LIGATURE LONG S T unfolded.
 */
public class LongSTLigatureFold29 {

  static String run(TokenStream ts) throws Exception {
    TermAttribute term = (TermAttribute) ts.addAttribute(TermAttribute.class);
    if (!ts.incrementToken()) {
      throw new AssertionError("no token");
    }
    String s = term.term();
    ts.close();
    return s;
  }

  static String ascii(String in) throws Exception {
    return run(new ASCIIFoldingFilter(new KeywordTokenizer(new StringReader(in))));
  }

  static String latin1(String in) throws Exception {
    return run(new ISOLatin1AccentFilter(new KeywordTokenizer(new StringReader(in))));
  }

  static String show(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x80) {
        b.append(c);
      } else {
        b.append(String.format("\\u%04X", (int) c));
      }
    }
    return b.toString();
  }

  static void control(String what, String got, String want) {
    System.out.println("control " + what + " = " + show(got));
    if (!want.equals(got)) {
      throw new IllegalStateException("CONTROL FAILED: " + what + " expected <" + want + "> but was <" + show(got) + ">");
    }
  }

  public static void main(String[] args) throws Exception {
    control("ASCIIFoldingFilter(fa+U+FB06)", ascii("faﬆ"), "fast");
    control("ASCIIFoldingFilter(fa+U+017F+t)", ascii("faſt"), "fast");
    System.out.println("context ISOLatin1AccentFilter(fa+U+FB05) = " + show(latin1("faﬅ")));
    String got = ascii("faﬅ");
    System.out.println("ASCIIFoldingFilter(fa+U+FB05) = " + show(got));
    if (!"fast".equals(got)) {
      throw new AssertionError("BUG: ASCIIFoldingFilter left U+FB05 unfolded: expected <fast> but was <" + show(got) + ">");
    }
    System.out.println("PASS: U+FB05 folded to st");
  }
}
