import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.composite.CompositeVerifyQuery;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.util.ShapeValuesPredicate;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.locationtech.spatial4j.context.SpatialContext;

/**
 * Plain IndexSearcher defaults (no query-cache setting at all): a CompositeVerifyQuery over a doc-values index query
 * keeps returning the pre-update count after an in-place doc-values update and a reopen. Exit 1 = stale count.
 * In the 10.x releases IndexSearcher caches by default; main disables the default cache, so main prints
 * "default query cache: null" and passes.
 */
public class DefaultSearcherStaleCount {
  public static void main(String[] args) throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    SerializedDVStrategy shapes = new SerializedDVStrategy(ctx, "shape");
    int numDocs = 10_050;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE).setMaxBufferedDocs(numDocs + 1).setRAMBufferSizeMB(256))) {
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        doc.add(new NumericDocValuesField("v", i % 2 == 0 ? 1 : 2));
        for (Field f : shapes.createIndexableFields(ctx.getShapeFactory().pointXY(i % 7, i % 5))) doc.add(f);
        w.addDocument(doc);
      }
      w.commit();
      Query query = new CompositeVerifyQuery(NumericDocValuesField.newSlowExactQuery("v", 1),
          new ShapeValuesPredicate(shapes.makeShapeValueSource(), SpatialOperation.Intersects,
              ctx.getShapeFactory().rect(-10, 10, -10, 10)));
      System.out.println("default query cache: " + IndexSearcher.getDefaultQueryCache());
      int before;
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        before = -1;
        for (int i = 0; i < 10; i++) before = new IndexSearcher(r1).count(query);
        System.out.println("before update: count=" + before);
        w.updateNumericDocValue(new Term("id", "0"), "v", 2L);
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          IndexSearcher uncached = new IndexSearcher(r2);
          uncached.setQueryCache(null);
          int truth = uncached.count(query);
          int got = new IndexSearcher(r2).count(query);
          System.out.println("after update: default searcher count=" + got + ", uncached count=" + truth);
          if (got != truth) {
            System.out.println("STALE: the default IndexSearcher returned the pre-update count");
            System.exit(1);
          }
          System.out.println("OK");
        }
      }
    }
  }
}
