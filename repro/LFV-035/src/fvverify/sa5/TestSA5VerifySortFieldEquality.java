package fvverify.sa5;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the finding: SortFields produced by DoubleValuesSource/LongValuesSource
 * getSortField(...) are not equal to identically-built SortFields.
 *
 * <p>Tests named test*Contract* assert the documented contract and are EXPECTED TO FAIL if the
 * candidate is real. Tests named testControl* are expected to pass.
 */
public class TestSA5VerifySortFieldEquality extends LuceneTestCase {

  private static String declaringEquals(Object o) throws Exception {
    return o.getClass().getMethod("equals", Object.class).getDeclaringClass().getName();
  }

  private static void dump(String label, SortField a, SortField b) throws Exception {
    System.out.println(
        "[SA5] "
            + label
            + ": a="
            + a
            + " b="
            + b
            + " a.equals(b)="
            + a.equals(b)
            + " a.hashCode()="
            + a.hashCode()
            + " b.hashCode()="
            + b.hashCode()
            + " a.getClass()="
            + a.getClass().getName()
            + " sortField.equals declared in="
            + declaringEquals(a)
            + " comparatorSource same instance="
            + (a.getComparatorSource() == b.getComparatorSource())
            + " comparatorSource.equals declared in="
            + (a.getComparatorSource() == null ? "n/a" : declaringEquals(a.getComparatorSource())));
  }

  // ---------------------------------------------------------------- expected to FAIL on main

  public void testDoubleContractEqualSourcesGiveEqualSortFields() throws Exception {
    DoubleValuesSource s1 = DoubleValuesSource.fromDoubleField("price");
    DoubleValuesSource s2 = DoubleValuesSource.fromDoubleField("price");
    // precondition: the sources themselves honour equals/hashCode
    assertEquals(s1, s2);
    assertEquals(s1.hashCode(), s2.hashCode());
    SortField a = s1.getSortField(false);
    SortField b = s2.getSortField(false);
    dump("double, equal sources", a, b);
    assertEquals(a.toString(), b.toString());
    assertEquals("reflexive", a, a);
    assertEquals("SortField from equal DoubleValuesSources", a, b);
  }

  public void testDoubleContractSameSourceTwiceGivesEqualSortFields() throws Exception {
    DoubleValuesSource s = DoubleValuesSource.fromDoubleField("price");
    SortField a = s.getSortField(true, 7.5d);
    SortField b = s.getSortField(true, 7.5d);
    dump("double, same instance, missingValue=7.5", a, b);
    assertEquals("SortField from the same DoubleValuesSource instance", a, b);
  }

  public void testLongContractEqualSourcesGiveEqualSorts() throws Exception {
    LongValuesSource s1 = LongValuesSource.fromLongField("ts");
    LongValuesSource s2 = LongValuesSource.fromLongField("ts");
    assertEquals(s1, s2);
    SortField a = s1.getSortField(true);
    SortField b = s2.getSortField(true);
    dump("long, equal sources", a, b);
    Sort sa = new Sort(a);
    Sort sb = new Sort(b);
    System.out.println(
        "[SA5] long Sort: "
            + sa
            + " vs "
            + sb
            + " equals="
            + sa.equals(sb)
            + " hash="
            + sa.hashCode()
            + "/"
            + sb.hashCode());
    assertEquals("Sort built from equal LongValuesSources", sa, sb);
  }

  /** A user-side cache keyed by Sort (a common pattern, e.g. a result cache) never hits. */
  public void testContractSortAsMapKey() throws Exception {
    Map<Sort, String> cache = new HashMap<>();
    cache.put(new Sort(DoubleValuesSource.fromDoubleField("price").getSortField(false)), "cached");
    String hit = cache.get(new Sort(DoubleValuesSource.fromDoubleField("price").getSortField(false)));
    System.out.println("[SA5] HashMap<Sort,...> lookup with identical sort -> " + hit);
    assertEquals("cache lookup with an identical Sort", "cached", hit);
  }

  /**
   * Expressions: LUCENE-5220 (95629b4f65a, 2013) gave ExpressionSortField value-based
   * equals/hashCode. LUCENE-7609 (8b055382d6c, 2017) replaced it with
   * DoubleValuesSource#getSortField. ExpressionValueSource itself implements equals.
   */
  public void testExpressionContractIdenticalSortFieldsEqual() throws Exception {
    Expression expr = JavascriptCompiler.compile("sqrt(_score) + ln(popularity)");
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("_score", DoubleValuesSource.SCORES);
    bindings.add("popularity", DoubleValuesSource.fromIntField("popularity"));
    // precondition: the underlying value sources are equal
    assertEquals(expr.getDoubleValuesSource(bindings), expr.getDoubleValuesSource(bindings));
    SortField a = expr.getSortField(bindings, true);
    SortField b = expr.getSortField(bindings, true);
    dump("expression, same expr+bindings", a, b);
    assertEquals("Expression#getSortField twice with same bindings", a, b);
  }

  // ---------------------------------------------------------------- controls, expected to PASS

  public void testControlOtherSortFieldsHonourValueEquality() throws Exception {
    SortField[][] pairs = {
      {new SortField("price", SortField.Type.DOUBLE), new SortField("price", SortField.Type.DOUBLE)},
      {
        new SortedNumericSortField("ts", SortField.Type.LONG, true),
        new SortedNumericSortField("ts", SortField.Type.LONG, true)
      },
      {FeatureField.newFeatureSort("feat", "pagerank"), FeatureField.newFeatureSort("feat", "pagerank")},
      {
        LatLonDocValuesField.newDistanceSort("loc", 10, 20),
        LatLonDocValuesField.newDistanceSort("loc", 10, 20)
      },
    };
    for (SortField[] p : pairs) {
      dump("control " + p[0].getClass().getSimpleName(), p[0], p[1]);
      assertEquals(p[0], p[1]);
      assertEquals(p[0].hashCode(), p[1].hashCode());
    }
  }

  /** Must stay unequal on main AND with any fix: different reverse/missingValue/field. */
  public void testControlDifferentParamsStayUnequal() {
    DoubleValuesSource d = DoubleValuesSource.fromDoubleField("price");
    assertNotEquals(d.getSortField(false), d.getSortField(true));
    assertNotEquals(d.getSortField(false, 1d), d.getSortField(false, 2d));
    assertNotEquals(d.getSortField(false), DoubleValuesSource.fromDoubleField("cost").getSortField(false));
    LongValuesSource l = LongValuesSource.fromLongField("ts");
    assertNotEquals(l.getSortField(false), l.getSortField(true));
    assertNotEquals(l.getSortField(false, 1L), l.getSortField(false, 2L));
    assertNotEquals(l.getSortField(false), LongValuesSource.fromLongField("ts2").getSortField(false));
  }

  /** Equality aside, the sort itself is correct: this is not a wrong-results bug. */
  public void testControlSortResultsAreCorrect() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      double[] prices = {3.5, -1.0, 10.25, 0.0, 7.0};
      for (int i = 0; i < prices.length; i++) {
        Document d = new Document();
        d.add(new DoubleDocValuesField("price", prices[i]));
        d.add(new NumericDocValuesField("ts", 100 - i));
        w.addDocument(d);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        TopFieldDocs td =
            s.search(
                MatchAllDocsQuery.INSTANCE,
                10,
                new Sort(DoubleValuesSource.fromDoubleField("price").getSortField(false)));
        StringBuilder sb = new StringBuilder();
        double prev = Double.NEGATIVE_INFINITY;
        for (ScoreDoc sd : td.scoreDocs) {
          double v = (Double) ((FieldDoc) sd).fields[0];
          sb.append(v).append(' ');
          assertTrue(v >= prev);
          prev = v;
        }
        System.out.println("[SA5] ascending sort by DoubleValuesSource price -> " + sb);
        TopFieldDocs tl =
            s.search(
                MatchAllDocsQuery.INSTANCE,
                10,
                new Sort(LongValuesSource.fromLongField("ts").getSortField(false)));
        long prevL = Long.MIN_VALUE;
        for (ScoreDoc sd : tl.scoreDocs) {
          long v = (Long) ((FieldDoc) sd).fields[0];
          assertTrue(v >= prevL);
          prevL = v;
        }
      }
    }
  }

  /** Mitigating factor claimed by the candidate: these sort fields cannot be index sorts. */
  public void testControlCannotBeIndexSort() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () ->
                new IndexWriterConfig()
                    .setIndexSort(
                        new Sort(DoubleValuesSource.fromDoubleField("price").getSortField(false))));
    System.out.println("[SA5] setIndexSort -> IAE: " + e.getMessage());
    expectThrows(
        IllegalArgumentException.class,
        () ->
            new IndexWriterConfig()
                .setIndexSort(new Sort(LongValuesSource.fromLongField("ts").getSortField(false))));
  }
}
