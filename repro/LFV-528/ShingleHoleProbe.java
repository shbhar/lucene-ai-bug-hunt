package probe;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/** Prints the tokens of WhitespaceTokenizer + StopFilter{a, of, the} + ShingleFilter, as term@position. */
public class ShingleHoleProbe {
  static final CharArraySet STOP = new CharArraySet(List.of("a", "of", "the"), false);

  static String run(String text, int min, int max, boolean uni, boolean uniIfNo) throws Exception {
    WhitespaceTokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(text));
    ShingleFilter sf = new ShingleFilter(new StopFilter(tok, STOP), min, max);
    sf.setOutputUnigrams(uni);
    sf.setOutputUnigramsIfNoShingles(uniIfNo);
    CharTermAttribute term = sf.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute inc = sf.addAttribute(PositionIncrementAttribute.class);
    TypeAttribute type = sf.addAttribute(TypeAttribute.class);
    List<String> all = new ArrayList<>();
    List<String> words = new ArrayList<>();
    int pos = -1;
    try (TokenStream ts = sf) {
      ts.reset();
      while (ts.incrementToken()) {
        pos += inc.getPositionIncrement();
        all.add("\"" + term + "\"@" + pos);
        if ("word".equals(type.type())) {
          words.add(term + "@" + pos);
        }
      }
      ts.end();
    }
    return String.format(
        "%-18s min=%d max=%d unigrams=%-5s ifNoShingles=%-5s words=%s  all=%s",
        "\"" + text + "\"", min, max, uni, uniIfNo, words, all);
  }

  public static void main(String[] args) throws Exception {
    System.out.println(run("a cup of tea", 3, 3, true, false));
    System.out.println(run("war of the worlds", 3, 3, true, false));
    System.out.println(run("the cat", 3, 3, true, false));
    System.out.println(run("the cat", 3, 4, true, false));
    System.out.println(run("one of two three", 4, 4, true, false));
    System.out.println(run("the cat", 3, 3, false, true));
    System.out.println("# controls");
    System.out.println(run("a cup of tea", 2, 2, true, false));
    System.out.println(run("a cup of tea", 2, 3, true, false));
    System.out.println(run("cup tea", 3, 3, true, false));
    System.out.println(run("war of the worlds of", 3, 3, true, false));
    System.out.println(run("please divide the sentence into shingles", 3, 3, true, false));
  }
}
