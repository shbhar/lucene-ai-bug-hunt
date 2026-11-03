package org.apache.lucene.util.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGraphTokenStreamFiniteStringsHeldStreams extends LuceneTestCase {

  public void testStreamsReadAfterIterating() throws Exception {
    GraphTokenStreamFiniteStrings graph =
        new GraphTokenStreamFiniteStrings(
            new CannedTokenStream(
                new Token("new", 1, 0, 3, 1),
                new Token("ny", 0, 0, 8, 2),
                new Token("york", 1, 4, 8, 1)));
    List<TokenStream> streams = new ArrayList<>();
    graph.getFiniteStrings().forEachRemaining(streams::add);

    Set<List<String>> paths = new HashSet<>();
    for (TokenStream ts : streams) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      List<String> path = new ArrayList<>();
      ts.reset();
      while (ts.incrementToken()) {
        path.add(term.toString());
      }
      ts.end();
      ts.close();
      paths.add(path);
    }
    // expected the graph's two paths {[new, york], [ny]}; actual {[ny, york], [ny]}
    assertEquals(Set.of(List.of("new", "york"), List.of("ny")), paths);
  }
}
