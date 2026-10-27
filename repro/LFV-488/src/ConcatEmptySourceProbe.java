// Release sweep probe (no test-framework dependency): "foo the" + "the" + "bar" with a stop
// filter, concatenated; as one text "foo the the bar" gives bar a position increment of 3.
import java.io.StringReader;
import java.util.Arrays;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ConcatenatingTokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

public class ConcatEmptySourceProbe {
  static TokenStream ws(String text) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return new StopFilter(t, new CharArraySet(Arrays.asList("the"), false));
  }

  static String run(TokenStream ts) throws Exception {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute pi = ts.addAttribute(PositionIncrementAttribute.class);
    StringBuilder sb = new StringBuilder();
    ts.reset();
    while (ts.incrementToken()) sb.append(term).append("+").append(pi.getPositionIncrement()).append(' ');
    ts.end();
    sb.append("end+").append(pi.getPositionIncrement());
    ts.close();
    return sb.toString();
  }

  public static void main(String[] a) throws Exception {
    String single = run(ws("foo the the bar"));
    String concat = run(new ConcatenatingTokenStream(ws("foo the"), ws("the"), ws("bar")));
    String concatEnd = run(new ConcatenatingTokenStream(ws("foo the"), ws("")));
    System.out.println("single: " + single + " | concat: " + concat + " | concat(\"foo the\",\"\"): " + concatEnd);
    boolean bug = !single.equals(concat) || !concatEnd.endsWith("end+1");
    System.exit(bug ? 1 : 0);
  }
}
