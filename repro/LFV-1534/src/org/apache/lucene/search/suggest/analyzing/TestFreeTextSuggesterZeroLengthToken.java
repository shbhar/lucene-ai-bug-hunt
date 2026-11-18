package org.apache.lucene.search.suggest.analyzing;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFreeTextSuggesterZeroLengthToken extends LuceneTestCase {

  // expected: no suggestion ends in a separator; actual: AssertionError (-ea) or "foo" + separator
  public void testNoEmptyWordIsPredicted() throws Exception {
    try (Analyzer analyzer = new ArabicAnalyzer()) {
      FreeTextSuggester suggester = new FreeTextSuggester(analyzer);
      suggester.build(new PlainTextDictionary(new StringReader("foo \u0640 bar")));
      for (LookupResult result : suggester.lookup("foo ", 10)) {
        CharSequence key = result.key;
        assertTrue(
            "a suggestion for \"foo \" predicts the empty word",
            key.charAt(key.length() - 1) != FreeTextSuggester.DEFAULT_SEPARATOR);
      }
    }
  }
}
