import java.util.Comparator;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

public class TestTopNSearcherIsCompleteOverflow extends LuceneTestCase {

  public void testIsCompleteWithMaxTopN() throws Exception {
    FSTCompiler<Long> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, PositiveIntOutputs.getSingleton()).build();
    compiler.add(Util.toIntsRef(new BytesRef("a"), new IntsRefBuilder()), 1L);
    compiler.add(Util.toIntsRef(new BytesRef("b"), new IntsRefBuilder()), 2L);
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());

    Util.TopNSearcher<Long> searcher =
        new Util.TopNSearcher<>(fst, Integer.MAX_VALUE, 1, Comparator.naturalOrder()) {
          @Override
          protected boolean acceptResult(IntsRef input, Long output) {
            return output != 1L;
          }
        };
    searcher.addStartPaths(fst.getFirstArc(new FST.Arc<>()), 0L, false, new IntsRefBuilder());
    Util.TopResults<Long> results = searcher.search();

    // expected: "b" (accepted) was dropped by the depth-1 queue, so empty and isComplete false; actual: isComplete true
    assertEquals(0, results.topN.size());
    assertFalse(results.isComplete);
  }
}
