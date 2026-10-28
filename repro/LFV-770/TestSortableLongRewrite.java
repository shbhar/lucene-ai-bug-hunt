package verifyv1;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValues;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;

/**
 * Independent check of DoubleValuesSource#toSortableLongDoubleValuesSource() under rewrite.
 *
 * <p>Contract used: the method javadoc ("Convert to LongValuesSource by calling
 * NumericUtils#doubleToSortableLong") and the LongValuesSource class javadoc (clients call
 * rewrite(IndexSearcher) against the top-level searcher, then getValues). Public API only; the
 * test lives outside the org.apache.lucene packages on purpose.
 */
public class TestSortableLongRewrite extends LuceneTestCase {

  /** docID i holds VALUES[i]; all distinct under Double.compare. */
  private static final double[] VALUES = {0.75, -0.5, 2.5, 0.25, -0.0};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double v : VALUES) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("v", v));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static LongValuesSource sortableSource() {
    return DoubleValuesSource.fromDoubleField("v").toSortableLongDoubleValuesSource();
  }

  private static long[] expectedSortableLongs() {
    long[] out = new long[VALUES.length];
    for (int i = 0; i < VALUES.length; i++) {
      out[i] = NumericUtils.doubleToSortableLong(VALUES[i]);
    }
    return out;
  }

  private long[] longValuesOf(LongValuesSource source) throws IOException {
    long[] out = new long[reader.maxDoc()];
    for (LeafReaderContext ctx : reader.leaves()) {
      LongValues values = source.getValues(ctx, null);
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        assertTrue(values.advanceExact(doc));
        out[ctx.docBase + doc] = values.longValue();
      }
    }
    return out;
  }

  private double[] doubleValuesOf(DoubleValuesSource source) throws IOException {
    double[] out = new double[reader.maxDoc()];
    for (LeafReaderContext ctx : reader.leaves()) {
      DoubleValues values = source.getValues(ctx, null);
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        assertTrue(values.advanceExact(doc));
        out[ctx.docBase + doc] = values.doubleValue();
      }
    }
    return out;
  }

  /** Control: without rewrite the documented encoding is produced. */
  public void testControlNoRewrite() throws IOException {
    assertArrayEquals(expectedSortableLongs(), longValuesOf(sortableSource()));
  }

  /** The documented protocol: rewrite against the top-level searcher, then getValues. */
  public void testDocumentedRewriteThenGetValues() throws IOException {
    LongValuesSource rewritten = sortableSource().rewrite(searcher);
    long[] actual = longValuesOf(rewritten);
    assertArrayEquals(
        "rewritten source "
            + rewritten
            + " produced "
            + Arrays.toString(actual)
            + " for doubles "
            + Arrays.toString(VALUES),
        expectedSortableLongs(),
        actual);
  }

  /**
   * Sorting by the source through IndexSearcher (which rewrites the Sort) must order documents
   * like a plain DOUBLE sort on the same field, and FieldDoc values must be the sortable longs.
   */
  public void testSortThroughIndexSearcher() throws IOException {
    // Oracle 1: order by Double.compare on the indexed values.
    Integer[] order = new Integer[VALUES.length];
    for (int i = 0; i < order.length; i++) order[i] = i;
    Arrays.sort(order, Comparator.comparingDouble((Integer i) -> VALUES[i]));
    int[] expectedDocs = Arrays.stream(order).mapToInt(Integer::intValue).toArray();

    // Oracle 2: Lucene's own DOUBLE sort on the same doc values field.
    TopFieldDocs ref =
        searcher.search(
            new MatchAllDocsQuery(), 10, new Sort(new SortField("v", SortField.Type.DOUBLE)));
    int[] refDocs = Arrays.stream(ref.scoreDocs).mapToInt(sd -> sd.doc).toArray();
    assertArrayEquals("oracle sanity", expectedDocs, refDocs);

    TopFieldDocs got =
        searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortableSource().getSortField(false)));
    int[] gotDocs = new int[got.scoreDocs.length];
    long[] gotKeys = new long[got.scoreDocs.length];
    for (int i = 0; i < got.scoreDocs.length; i++) {
      ScoreDoc sd = got.scoreDocs[i];
      gotDocs[i] = sd.doc;
      gotKeys[i] = (Long) ((FieldDoc) sd).fields[0];
    }
    assertArrayEquals(
        "hit order; sort keys were " + Arrays.toString(gotKeys), expectedDocs, gotDocs);
    long[] expectedKeys = new long[expectedDocs.length];
    for (int i = 0; i < expectedDocs.length; i++) {
      expectedKeys[i] = NumericUtils.doubleToSortableLong(VALUES[expectedDocs[i]]);
    }
    assertArrayEquals("sort keys", expectedKeys, gotKeys);
  }

  /** The double view (toDoubleValuesSource) of the sortable source, rewritten as consumers do. */
  public void testDoubleViewAfterRewrite() throws IOException {
    DoubleValuesSource view = sortableSource().toDoubleValuesSource();
    double[] expected = new double[VALUES.length];
    long[] sortable = expectedSortableLongs();
    for (int i = 0; i < expected.length; i++) expected[i] = (double) sortable[i];
    // un-rewritten: the documented composition
    assertArrayEquals(expected, doubleValuesOf(view), 0d);
    // rewritten (what FunctionScoreQuery / rescorers do in createWeight)
    DoubleValuesSource rewritten = view.rewrite(searcher);
    assertArrayEquals("rewritten view " + rewritten, expected, doubleValuesOf(rewritten), 0d);
  }
}
