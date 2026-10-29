package org.apache.lucene.search;

import java.util.NoSuchElementException;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDoubleValuesSourceRescorerNoSegments extends LuceneTestCase {

  public void testRescoreOnReaderWithoutSegments() throws Exception {
    IndexSearcher searcher = new IndexSearcher(new MultiReader());
    TopDocs firstPass = searcher.search(MatchAllDocsQuery.INSTANCE, 10);
    Rescorer rescorer =
        new DoubleValuesSourceRescorer(DoubleValuesSource.constant(1)) {
          @Override
          protected float combine(float firstPassScore, boolean valuePresent, double sourceValue) {
            return (float) sourceValue;
          }
        };
    TopDocs rescored;
    try {
      rescored = rescorer.rescore(searcher, firstPass, 10);
    } catch (NoSuchElementException e) {
      throw new AssertionError("expected 0 rescored hits (as QueryRescorer returns), got " + e, e);
    }
    assertEquals(0, rescored.scoreDocs.length);
  }
}
