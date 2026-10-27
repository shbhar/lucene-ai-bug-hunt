package repro;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Release-portable check (no test framework): the final position increment after
 * WhitespaceTokenizer + StopFilter("the") on "abc the" is 1; an [Edge]NGramTokenFilter(3, 3) that
 * keeps "abc" must report the same. Exit 1 when either filter loses the trailing hole.
 */
public class NGramEndPosIncMain {
  static TokenStream stop() {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader("abc the"));
    return new StopFilter(t, new CharArraySet(Arrays.asList("the"), false));
  }

  static TokenStream wrap(String cls, TokenStream in) throws Exception {
    Class<?> c = Class.forName(cls);
    try {
      Constructor<?> k = c.getConstructor(TokenStream.class, int.class, int.class, boolean.class);
      return (TokenStream) k.newInstance(in, 3, 3, false);
    } catch (NoSuchMethodException e) {
      Constructor<?> k = c.getConstructor(TokenStream.class, int.class, int.class);
      return (TokenStream) k.newInstance(in, 3, 3);
    }
  }

  static int finalPosInc(TokenStream ts) throws Exception {
    PositionIncrementAttribute pi = ts.addAttribute(PositionIncrementAttribute.class);
    ts.reset();
    int n = 0;
    while (ts.incrementToken()) n++;
    ts.end();
    int p = pi.getPositionIncrement();
    ts.close();
    return n == 1 ? p : -100 - n;
  }

  public static void main(String[] args) throws Exception {
    int control = finalPosInc(stop());
    System.out.println("control StopFilter final posInc = " + control);
    int fails = 0;
    for (String cls :
        new String[] {
          "org.apache.lucene.analysis.ngram.NGramTokenFilter",
          "org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter"
        }) {
      int p = finalPosInc(wrap(cls, stop()));
      System.out.println(cls + " final posInc = " + p + (p == control ? " OK" : " LOST HOLE"));
      if (p != control) fails++;
    }
    System.exit(fails == 0 && control == 1 ? 0 : 1);
  }
}
