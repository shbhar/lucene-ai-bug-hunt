package org.apache.lucene.search.highlight;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Highlight of a MultiPhraseQuery should not depend on a constant shift of all its positions. */
public class TestMpqOffsetInvariance extends LuceneTestCase {
  private static final String F = "f";

  private static String hl(Query q, String text) throws Exception {
    Highlighter h = new Highlighter(new SimpleHTMLFormatter(), new QueryScorer(q, F));
    h.setTextFragmenter(new NullFragmenter());
    return h.getBestFragment(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false), F, text);
  }

  private static Query mpq(int shift, int slop) {
    return new MultiPhraseQuery.Builder()
        .add(new Term[] {new Term(F, "a"), new Term(F, "x")}, shift)
        .add(new Term[] {new Term(F, "b")}, shift + 1)
        .setSlop(slop)
        .build();
  }

  public void testShiftInvariance() throws Exception {
    // a b: adjacent (matches slop 0); a z b: 1 apart; a z z b: 2 apart; a z z z b: 3 apart
    String[] texts = {"a z b", "a z z b", "a z z z b", "b a"};
    StringBuilder report = new StringBuilder();
    boolean differ = false;
    for (int slop : new int[] {0, 1}) {
      for (String t : texts) {
        String base = hl(mpq(0, slop), t);
        for (int shift : new int[] {1, 2, 3}) {
          String s = hl(mpq(shift, slop), t);
          report.append("slop=" + slop + " shift=" + shift + " text='" + t + "' base=" + base + " shifted=" + s + "\n");
          if (String.valueOf(base).equals(String.valueOf(s)) == false) differ = true;
        }
      }
    }
    System.out.println(report);
    assertFalse("highlight depends on a constant position shift:\n" + report, differ);
  }
}
