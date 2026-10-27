import java.io.Reader;
import java.io.StringReader;
import org.apache.lucene.analysis.ja.JapaneseIterationMarkCharFilter;

/** Plain main() variant (no test framework) for old releases; exits 1 when the bug reproduces. */
public class IterationMarkUnvoicedKanaMain {
  public static void main(String[] args) throws Exception {
    String in = "なゝめ";
    StringBuilder sb = new StringBuilder();
    try (Reader r = new JapaneseIterationMarkCharFilter(new StringReader(in))) {
      int c;
      while ((c = r.read()) != -1) sb.append((char) c);
    }
    System.out.println(in + " -> " + sb + " (expected ななめ)");
    if (!sb.toString().equals("ななめ")) {
      System.exit(1);
    }
  }
}
