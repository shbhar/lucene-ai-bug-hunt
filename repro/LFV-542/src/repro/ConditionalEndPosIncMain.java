package repro;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ProtectedTermFilter;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Release-portable check: WhitespaceTokenizer + StopFilter("the") on "abc the" ends with final
 * position increment 1; wrapping a pass-through filter in ProtectedTermFilter (protected word "x")
 * must not change it. Exit 1 when the hole is lost.
 */
public class ConditionalEndPosIncMain {
  static final class PassThrough extends TokenFilter {
    PassThrough(TokenStream in) {
      super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
      return input.incrementToken();
    }
  }

  static TokenStream stop(String s) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(s));
    return new StopFilter(t, new CharArraySet(Arrays.asList("the"), false));
  }

  static int finalPosInc(TokenStream ts) throws IOException {
    PositionIncrementAttribute pi = ts.addAttribute(PositionIncrementAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {}
    ts.end();
    int p = pi.getPositionIncrement();
    ts.close();
    return p;
  }

  public static void main(String[] args) throws Exception {
    CharArraySet prot = new CharArraySet(Arrays.asList("x"), false);
    int control = finalPosInc(stop("abc the"));
    int wrapped = finalPosInc(new ProtectedTermFilter(prot, stop("abc the"), PassThrough::new));
    int protectedLast = finalPosInc(new ProtectedTermFilter(prot, stop("x the"), PassThrough::new));
    System.out.println("StopFilter only: final posInc = " + control);
    System.out.println("ProtectedTermFilter(pass-through), last token filtered: final posInc = " + wrapped);
    System.out.println("ProtectedTermFilter(pass-through), last token protected: final posInc = " + protectedLast);
    System.exit(control == 1 && wrapped == 1 && protectedLast == 1 ? 0 : 1);
  }
}
