import java.io.StringReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.DecimalDigitFilter;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilter;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.th.ThaiNormalizationFilter;
import org.apache.lucene.analysis.th.ThaiRepeatFilter;
import org.apache.lucene.analysis.th.ThaiTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/** Prints JDK word segments, ThaiTokenizer tokens (with offsets) and ThaiAnalyzer terms. */
public class ThaiPrefixProbe {
  static String segments(String s) {
    BreakIterator bi = BreakIterator.getWordInstance(new Locale.Builder().setLanguageTag("th").build());
    bi.setText(s);
    List<String> out = new ArrayList<>();
    for (int a = bi.first(), b = bi.next(); b != BreakIterator.DONE; a = b, b = bi.next()) {
      out.add("[" + s.substring(a, b) + "]");
    }
    return String.join("", out);
  }

  static String tokens(Tokenizer t, String s) throws Exception {
    t.setReader(new StringReader(s));
    return dump(t, true);
  }

  static String dump(TokenStream ts, boolean offsets) throws Exception {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term + (offsets ? "[" + off.startOffset() + "," + off.endOffset() + ")" : ""));
    }
    ts.end();
    ts.close();
    return out.toString();
  }

  /** Workaround for released versions: a char filter that puts a space between the prefix and the number. */
  static Analyzer workaround() {
    Pattern p = Pattern.compile("(?<!\\p{N})([.#\\p{Sc}])(?=\\p{N})");
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String field) {
        Tokenizer src = new ThaiTokenizer();
        TokenStream r = new LowerCaseFilter(src);
        r = new DecimalDigitFilter(r);
        r = new ThaiNormalizationFilter(r);
        r = new ThaiRepeatFilter(r);
        r = new StopFilter(r, ThaiAnalyzer.getDefaultStopSet());
        return new TokenStreamComponents(src, r);
      }

      @Override
      protected java.io.Reader initReader(String field, java.io.Reader reader) {
        return new PatternReplaceCharFilter(p, "$1 ", reader);
      }
    };
  }

  public static void main(String[] args) throws Exception {
    System.out.println("java.version=" + System.getProperty("java.version") + " DBBI=" + ThaiTokenizer.DBBI_AVAILABLE);
    String[] inputs = {
      "ราคา ฿1,500", "ราคา $100", "ราคา €20", "ราคา ¢50", "พ.ศ.2567", "ค.ศ.2024", "ม.ค.67", "ข้อ #3", "ค่า .5",
      "page.42", "No.5", "abc.123", "#1word",
      // controls
      "ราคา 1,500 บาท", "พ.ศ. 2567", "1.5", "50%", "ราคา - บาท", "...", "้ำ"
    };
    try (Analyzer thai = new ThaiAnalyzer();
        Analyzer wa = workaround()) {
      for (String s : inputs) {
        System.out.println("INPUT " + s);
        System.out.println("  jdk segments       " + segments(s));
        System.out.println("  ThaiTokenizer      " + tokens(new ThaiTokenizer(), s));
        System.out.println("  ThaiAnalyzer       " + dump(thai.tokenStream("f", s), false));
        System.out.println("  workaround filter  " + dump(wa.tokenStream("f", s), false));
      }
    }
    // user dictionary interplay (the fix moves the start of the dictionary lookup)
    String[][] dictCases = {
      {"1,500บาท", "฿1,500บาท"}, {"ราคา", "ราคา ฿1,500"}, {"฿1,500", "ราคา ฿1,500"}, {"2567", "พ.ศ.2567"},
      {"แม่น", "แม่น้ำ"}
    };
    for (String[] c : dictCases) {
      CharArraySet dict = new CharArraySet(List.of(c[0]), false);
      System.out.println("DICT {" + c[0] + "} INPUT " + c[1]);
      System.out.println("  jdk segments       " + segments(c[1]));
      System.out.println("  ThaiTokenizer      " + tokens(new ThaiTokenizer(dict), c[1]));
    }
  }
}
