import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.grouping.GroupReducer;
import org.apache.lucene.search.grouping.SearchGroup;
import org.apache.lucene.search.grouping.SecondPassGroupingCollector;
import org.apache.lucene.search.grouping.TermGroupSelector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

public class TestGroupReducerSharedScorable {

  // expected: group b's collector returns its one matching document; actual: it returns no hit
  @Test
  public void testOtherGroupKeepsItsDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String g : new String[] {"a", "a", "b"}) {
          Document doc = new Document();
          doc.add(new StringField("f", "x", Field.Store.NO));
          doc.add(new SortedDocValuesField("g", new BytesRef(g)));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SearchGroup<BytesRef> a = new SearchGroup<>();
        a.groupValue = new BytesRef("a");
        SearchGroup<BytesRef> b = new SearchGroup<>();
        b.groupValue = new BytesRef("b");
        GroupReducer<BytesRef, TopScoreDocCollector> reducer =
            new GroupReducer<>() {
              @Override
              public boolean needsScores() {
                return true;
              }

              @Override
              protected TopScoreDocCollector newCollector() {
                return new TopScoreDocCollectorManager(1, null, 1).newCollector();
              }
            };
        SecondPassGroupingCollector<BytesRef> collector =
            new SecondPassGroupingCollector<>(new TermGroupSelector("g"), List.of(a, b), reducer);
        new IndexSearcher(reader)
            .search(
                new TermQuery(new Term("f", "x")),
                new CollectorManager<SecondPassGroupingCollector<BytesRef>, Void>() {
                  @Override
                  public SecondPassGroupingCollector<BytesRef> newCollector() {
                    return collector;
                  }

                  @Override
                  public Void reduce(Collection<SecondPassGroupingCollector<BytesRef>> collectors) {
                    return null;
                  }
                });
        assertEquals(1, reducer.getCollector(new BytesRef("b")).topDocs().scoreDocs.length);
      }
    }
  }
}
