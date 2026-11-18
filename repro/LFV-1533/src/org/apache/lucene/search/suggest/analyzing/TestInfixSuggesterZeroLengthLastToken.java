package org.apache.lucene.search.suggest.analyzing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestInfixSuggesterZeroLengthLastToken extends LuceneTestCase {

  public void testKeyEndingInZeroLengthToken() throws Exception {
    try (Analyzer analyzer = new ArabicAnalyzer();
        AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(newDirectory(), analyzer)) {
      suggester.add(new BytesRef("foo \u0640 bar"), null, 1, null);
      suggester.refresh();
      // expected: 1 (the key begins the suggestion); actual: 0 (U+0640 analyzes to "")
      assertEquals(1, suggester.lookup("foo \u0640", 10, true, false).size());
    }
  }
}
