import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.sandbox.search.LargeNumHitsTopDocsCollector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestLargeNumHitsTopDocsHowMany {

  private static ScoreDoc[] topDocsOfCollectorForTwoHits(int howMany) throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String text : new String[] {"a b b", "a a b", "a a a"}) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        return new IndexSearcher(reader)
            .search(
                new TermQuery(new Term("f", "a")),
                new CollectorManager<LargeNumHitsTopDocsCollector, ScoreDoc[]>() {
                  @Override
                  public LargeNumHitsTopDocsCollector newCollector() {
                    return new LargeNumHitsTopDocsCollector(2);
                  }

                  @Override
                  public ScoreDoc[] reduce(Collection<LargeNumHitsTopDocsCollector> collectors) {
                    return collectors.iterator().next().topDocs(howMany).scoreDocs;
                  }
                });
      }
    }
  }

  @Test
  public void testHowManyBelowRequestedHitCount() throws IOException {
    // expected: document 2, the best of the three hits; actual: document 1, the second best
    assertEquals(2, topDocsOfCollectorForTwoHits(1)[0].doc);
  }

  @Test
  public void testHowManyAboveRequestedHitCount() throws IOException {
    // expected: the two kept hits and no null entry; actual: [null, document 2, document 1]
    assertNotNull("scoreDocs[0] of topDocs(3)", topDocsOfCollectorForTwoHits(3)[0]);
  }
}
