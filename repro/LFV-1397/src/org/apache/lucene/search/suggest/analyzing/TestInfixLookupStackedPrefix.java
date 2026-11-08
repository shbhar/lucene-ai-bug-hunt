package org.apache.lucene.search.suggest.analyzing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestInfixLookupStackedPrefix extends LuceneTestCase {

  public void testAccentedPrefixOfUnfinishedWord() throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            return new TokenStreamComponents(tokenizer, new ASCIIFoldingFilter(tokenizer, true));
          }
        };
    try (AnalyzingInfixSuggester suggester =
        new AnalyzingInfixSuggester(newDirectory(), analyzer)) {
      suggester.add(new BytesRef("naïve"), null, 1, null);
      suggester.refresh();
      // expected: "naï" (a prefix of "naïve") suggests "naïve"; actual: no suggestions
      assertEquals(1, suggester.lookup("naï", 10, true, false).size());
    }
    analyzer.close();
  }
}
