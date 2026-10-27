package repro;

import java.lang.reflect.Constructor;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Release-portable check (no test framework, no tokenizer): one-token streams through
 * CapitalizationFilter. Exit 1 when a letter outside the BMP is not case-mapped.
 */
public class CapitalizationSupplementaryMain {
  static final class One extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final String text;
    private boolean done;

    One(String text) {
      this.text = text;
    }

    @Override
    public boolean incrementToken() {
      if (done) return false;
      clearAttributes();
      term.setEmpty().append(text);
      done = true;
      return true;
    }

    @Override
    public void reset() throws java.io.IOException {
      super.reset();
      done = false;
    }
  }

  static String run(String text, boolean allWords) throws Exception {
    Class<?> c = Class.forName("org.apache.lucene.analysis.miscellaneous.CapitalizationFilter");
    TokenStream ts = null;
    for (Constructor<?> k : c.getConstructors()) {
      if (allWords && k.getParameterCount() == 8) {
        ts = (TokenStream) k.newInstance(new One(text), false, null, true, null, 0,
            Integer.MAX_VALUE, Integer.MAX_VALUE);
      } else if (!allWords && k.getParameterCount() == 1) {
        ts = (TokenStream) k.newInstance(new One(text));
      }
    }
    CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    ts.incrementToken();
    String out = t.toString();
    ts.end();
    ts.close();
    return out;
  }

  static String cp(int... c) {
    return new String(c, 0, c.length);
  }

  static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints().forEach(c -> sb.append(String.format(" U+%04X", c)));
    return sb.toString().trim();
  }

  public static void main(String[] args) throws Exception {
    String[][] cases = {
      // input, mode, expected (default settings: first word only)
      {"ab", "first", "Ab"}, // control
      {cp(0x1E922, 0x1E923), "first", cp(0x1E900, 0x1E923)}, // Adlam small alif + daali
      {cp(0x1E900, 0x1E901), "first", cp(0x1E900, 0x1E923)}, // Adlam capital alif + capital daali
      {cp('a', 'B', 0x104B0), "first", cp('A', 'b', 0x104D8)}, // Osage capital A after BMP letters
    };
    int fails = 0;
    for (String[] k : cases) {
      String got = run(k[0], k[1].equals("all"));
      boolean ok = got.equals(k[2]);
      System.out.println("[" + hex(k[0]) + "] -> [" + hex(got) + "] expected [" + hex(k[2]) + "] " + (ok ? "OK" : "WRONG"));
      if (!ok) fails++;
    }
    System.exit(fails == 0 ? 0 : 1);
  }
}
