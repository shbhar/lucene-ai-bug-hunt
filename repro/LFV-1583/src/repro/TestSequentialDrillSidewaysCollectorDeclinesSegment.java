package repro;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.DrillSideways;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.internal.hppc.LongIntHashMap;
import org.apache.lucene.sandbox.facet.plain.histograms.HistogramCollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSequentialDrillSidewaysCollectorDeclinesSegment extends LuceneTestCase {

  // Expected: the search returns and bucket 0 of the "price" histogram counts 1. Actual: the
  // search throws CollectionTerminatedException.
  public void testSidewaysCollectorThatDeclinesASegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        Document doc = new Document();
        doc.add(new StringField("color", "red", Field.Store.NO));
        doc.add(new NumericDocValuesField("price", 5));
        w.addDocument(doc);
        w.commit();
        doc = new Document();
        doc.add(new StringField("color", "red", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        FacetsConfig config = new FacetsConfig();
        DrillDownQuery query = new DrillDownQuery(config);
        query.add("color", new TermQuery(new Term("color", "red")));
        DrillSideways drillSideways =
            new DrillSideways(new IndexSearcher(reader), config, (TaxonomyReader) null);

        DrillSideways.Result<?, LongIntHashMap> result =
            drillSideways.search(
                query,
                new TopScoreDocCollectorManager(10, 10),
                List.of(new HistogramCollectorManager("price", 10)));

        assertEquals(1, result.drillSidewaysResults().get(0).get(0));
      }
    }
  }
}
