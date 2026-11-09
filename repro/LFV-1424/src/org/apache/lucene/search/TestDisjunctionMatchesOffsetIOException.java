package org.apache.lucene.search;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDisjunctionMatchesOffsetIOException extends LuceneTestCase {

  private static final class FailingOffsets implements MatchesIterator {
    @Override
    public boolean next() {
      return true;
    }

    @Override
    public int startPosition() {
      return -1;
    }

    @Override
    public int endPosition() {
      return -1;
    }

    @Override
    public int startOffset() throws IOException {
      throw new IOException("offset read failed");
    }

    @Override
    public int endOffset() throws IOException {
      throw new IOException("offset read failed");
    }

    @Override
    public MatchesIterator getSubMatches() {
      return null;
    }

    @Override
    public Query getQuery() {
      return MatchNoDocsQuery.INSTANCE;
    }
  }

  public void testOffsetIOExceptionPropagates() {
    // expected: the declared IOException; actual: IllegalArgumentException("Failed to retrieve term offset")
    expectThrows(
        IOException.class,
        () -> MatchesUtils.disjunction(List.of(new FailingOffsets(), new FailingOffsets())));
  }
}
