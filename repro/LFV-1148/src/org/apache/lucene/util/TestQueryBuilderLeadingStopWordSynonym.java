package org.apache.lucene.util;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryBuilderLeadingStopWordSynonym extends LuceneTestCase {

  // Expected: SynonymQuery(dogs, dog), as for "dogs"; actual: one-position MultiPhraseQuery "(dogs dog)".
  public void testLeadingStopWordBeforeSynonym() {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, true);
            CharArraySet stopWords = new CharArraySet(List.of("the"), false);
            return new TokenStreamComponents(
                tokenizer, new MockSynonymFilter(new StopFilter(tokenizer, stopWords)));
          }
        };
    SynonymQuery expected =
        new SynonymQuery.Builder("field")
            .addTerm(new Term("field", "dogs"))
            .addTerm(new Term("field", "dog"))
            .build();
    assertEquals(expected, new QueryBuilder(analyzer).createPhraseQuery("field", "the dogs"));
  }
}
