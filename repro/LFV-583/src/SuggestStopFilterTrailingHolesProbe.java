import java.io.StringReader;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilter;

/** main() probe for 8.x jars (no test framework): exits 1 when the final position increments differ. */
public class SuggestStopFilterTrailingHolesProbe {
  static int finalPosInc(TokenStream ts) throws Exception {
    PositionIncrementAttribute p = ts.addAttribute(PositionIncrementAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {}
    ts.end();
    int r = p.getPositionIncrement();
    ts.close();
    return r;
  }

  static Tokenizer tok(String s) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(s));
    return t;
  }

  public static void main(String[] a) throws Exception {
    CharArraySet stop = StopFilter.makeStopSet("to", "a", "the", "of");
    String text = "go to a the ";
    int s = finalPosInc(new StopFilter(tok(text), stop));
    int ss = finalPosInc(new SuggestStopFilter(tok(text), stop));
    System.out.println("StopFilter final posInc=" + s + " SuggestStopFilter final posInc=" + ss);
    if (s != ss) {
      System.out.println("MISMATCH");
      System.exit(1);
    }
  }
}
