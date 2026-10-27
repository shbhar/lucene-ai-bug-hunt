import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Version-neutral history probe for KoreanTokenizer (nori), usable on every release since the module
 * was added (7.4.0). It uses only the 4-argument public constructor, which exists in all of them and
 * which (since 8.2.0) delegates with discardPunctuation=true; before 8.2.0 punctuation was always
 * discarded.
 *
 * <p>Exit codes: 0 = no punctuation or whitespace token in unigram mode (bug absent); 1 = a
 * punctuation or whitespace token was emitted in unigram mode (bug present); 2 = inconclusive (a
 * control failed: unigram mode not active, or the non-unigram control already leaks punctuation).
 */
public class KoreanUnigramPunctHistoryProbe {

  static final String[] INPUTS = {
    "가격: $100", "다음\n\n문장", "test@example.com", "ab  cd", "(테스트) 문장..."
  };

  static List<String> tokens(String text, boolean unigrams) throws Exception {
    KoreanTokenizer t =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            unigrams);
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString());
    }
    t.end();
    t.close();
    return out;
  }

  static String show(List<String> toks) {
    List<String> s = new ArrayList<>();
    for (String x : toks) {
      s.add(x.replace("\n", "\\n").replace("\t", "\\t"));
    }
    return s.toString();
  }

  static List<String> nonWordTokens(List<String> toks) {
    List<String> bad = new ArrayList<>();
    for (String x : toks) {
      if (Character.isLetterOrDigit(x.codePointAt(0)) == false) {
        bad.add(x.replace("\n", "\\n").replace("\t", "\\t"));
      }
    }
    return bad;
  }

  public static void main(String[] args) throws Exception {
    boolean bug = false;
    boolean controlsOk = true;
    for (String in : INPUTS) {
      List<String> plain = tokens(in, false);
      List<String> uni = tokens(in, true);
      List<String> badPlain = nonWordTokens(plain);
      List<String> badUni = nonWordTokens(uni);
      String label = in.replace("\n", "\\n");
      System.out.println("input=\"" + label + "\"");
      System.out.println("  unigrams=false -> " + show(plain));
      System.out.println("  unigrams=true  -> " + show(uni));
      if (badPlain.isEmpty() == false) {
        System.out.println("  CONTROL FAILED: non-unigram output already has " + badPlain);
        controlsOk = false;
      }
      if (badUni.isEmpty() == false) {
        System.out.println("  LEAK: punctuation/whitespace tokens in unigram mode: " + badUni);
        bug = true;
      }
    }
    // Control: unigram mode must really be active (an unknown Latin word is split into letters).
    List<String> u = tokens("test@example.com", true);
    boolean unigramActive = u.contains("t") && u.contains("x") && u.contains("test") == false;
    System.out.println("control unigram-mode-active=" + unigramActive);
    if (unigramActive == false) {
      controlsOk = false;
    }
    if (controlsOk == false) {
      System.out.println("VERDICT: INCONCLUSIVE");
      System.exit(2);
    }
    if (bug) {
      System.out.println("VERDICT: BUG PRESENT");
      System.exit(1);
    }
    System.out.println("VERDICT: BUG ABSENT");
  }
}
