/*
 * Probe for LFV-607 (brief step, 2026-10-28). Prints, for each input: the output of
 * JapaneseIterationMarkCharFilter with default flags, the expected expansion, the output with a
 * MappingCharFilter workaround in front (20 pairs: なゝ => なな, ナヽ => ナナ, ...), and the tokens of
 * JapaneseTokenizer (search mode, no user dictionary) behind the filter. Exit 1 if any plain-filter
 * output differs from the expected expansion, else 0.
 */
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.ja.JapaneseIterationMarkCharFilter;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class IterationMarkProbe {

  // {input, expected expansion, kind}
  static final String[][] CASES = {
    {"なゝめ", "ななめ", "bug"},
    {"のゝしる", "ののしる", "bug"},
    {"なゝつ", "ななつ", "bug"},
    {"ナヽメ", "ナナメ", "bug"},
    {"ぱゝ", "ぱぱ", "bug"},
    {"っゝ", "っっ", "bug"},
    {"なのゝゝ", "なのなの", "bug (span of two marks)"},
    {"なゞ", "なな", "control (voiced mark)"},
    {"ぽゝ", "ぽぽ", "control (upstream test)"},
    {"じゝ", "じし", "control (upstream test)"},
    {"おゝの", "おおの", "control (upstream test)"},
    {"こゝろ", "こころ", "control"},
    {"ところゞゝゝ", "ところどころ", "control (upstream test)"},
  };

  static final String UNVOICED_SOURCES_H = "っなにぬねのぱぴぷぺ";
  static final String UNVOICED_SOURCES_K = "ッナニヌネノパピプペ";

  public static void main(String[] args) throws IOException {
    NormalizeCharMap.Builder b = new NormalizeCharMap.Builder();
    for (int i = 0; i < UNVOICED_SOURCES_H.length(); i++) {
      char h = UNVOICED_SOURCES_H.charAt(i);
      char k = UNVOICED_SOURCES_K.charAt(i);
      b.add("" + h + 'ゝ', "" + h + h);
      b.add("" + k + 'ヽ', "" + k + k);
    }
    NormalizeCharMap workaround = b.build();

    Analyzer ja =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new JapaneseTokenizer(null, true, JapaneseTokenizer.Mode.SEARCH);
            return new TokenStreamComponents(t, t);
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new JapaneseIterationMarkCharFilter(reader);
          }
        };

    int wrong = 0;
    for (String[] c : CASES) {
      String plain = read(new JapaneseIterationMarkCharFilter(new StringReader(c[0])));
      String withWorkaround =
          read(
              new JapaneseIterationMarkCharFilter(
                  new MappingCharFilter(workaround, new StringReader(c[0]))));
      boolean ok = plain.equals(c[1]);
      if (!ok) {
        wrong++;
      }
      System.out.println(
          (ok ? "ok    " : "WRONG ")
              + c[0]
              + " -> "
              + plain
              + " (expected "
              + c[1]
              + "; with MappingCharFilter workaround: "
              + withWorkaround
              + "; JapaneseTokenizer tokens: "
              + tokens(ja, c[0])
              + ") ["
              + c[2]
              + "]");
    }
    // the sentence-level effect through the tokenizer, with and without the mark
    for (String s : new String[] {"なゝめに切る", "ななめに切る", "のゝしる声", "ののしる声"}) {
      System.out.println("tokens " + s + " -> " + tokens(ja, s));
    }
    ja.close();
    System.out.println("wrong=" + wrong + " of " + CASES.length);
    System.exit(wrong == 0 ? 0 : 1);
  }

  static String read(Reader r) throws IOException {
    StringBuilder sb = new StringBuilder();
    int ch;
    while ((ch = r.read()) != -1) {
      sb.append((char) ch);
    }
    r.close();
    return sb.toString();
  }

  static List<String> tokens(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }
}
