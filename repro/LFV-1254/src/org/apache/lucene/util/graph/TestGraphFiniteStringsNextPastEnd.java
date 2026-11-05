package org.apache.lucene.util.graph;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGraphFiniteStringsNextPastEnd extends LuceneTestCase {

  // Expected: next() past the end throws NoSuchElementException; actual: NullPointerException (AssertionError with -ea).
  public void testNextAfterLastPath() throws Exception {
    Iterator<TokenStream> it =
        new GraphTokenStreamFiniteStrings(new CannedTokenStream(new Token("x", 0, 1)))
            .getFiniteStrings();
    it.next();
    assertFalse(it.hasNext());
    expectThrows(NoSuchElementException.class, it::next);
  }
}
