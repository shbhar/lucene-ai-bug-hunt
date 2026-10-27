/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.facet.verify;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.LongRangeDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.RangeFieldQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.facet.range.DoubleRange;
import org.apache.lucene.facet.range.DoubleRangeFacetCounts;
import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.facet.rangeonrange.LongRangeOnRangeFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent repro (public API only). FacetResult.value is documented as the "Total number of
 * documents containing a value for this path"; for range facets that is the number of documents
 * that fall into at least one range (upstream tests assert e.g. value=22 of 101 docs). The
 * fastMatchQuery only decides "whether the document should be checked for matching ranges".
 *
 * <p>Index: 10 docs with value v = 0..9 in a long field, a double field and a long range
 * doc-values field [v, v]. One facet range [0, 2] (docs 0, 1, 2). The fastMatchQuery selects
 * v in [0, 4], a superset of the range, so it only removes docs that are in no range. The total
 * must stay 3.
 */
public class TestRangeFacetTotalFastMatchV1 extends LuceneTestCase {

  private static final int NUM_DOCS = 10;

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  private void buildIndex(int segments) throws IOException {
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int perSegment = NUM_DOCS / segments;
      for (int v = 0; v < NUM_DOCS; v++) {
        Document doc = new Document();
        doc.add(new LongPoint("num", v));
        doc.add(new NumericDocValuesField("num", v));
        doc.add(new DoublePoint("dbl", v));
        doc.add(new DoubleDocValuesField("dbl", v));
        doc.add(new LongRangeDocValuesField("rr", new long[] {v}, new long[] {v}));
        w.addDocument(doc);
        if ((v + 1) % perSegment == 0) {
          w.commit(); // one segment per block, never merged
        }
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals(segments, reader.leaves().size());
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    if (reader != null) {
      reader.close();
    }
    if (dir != null) {
      dir.close();
    }
    super.tearDown();
  }

  @SuppressWarnings("deprecation")
  private FacetsCollector matchAll() throws IOException {
    return searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
  }

  private static LongRange[] longRanges() {
    return new LongRange[] {new LongRange("low", 0, true, 2, true)};
  }

  private static Query fastMatch() {
    return LongPoint.newRangeQuery("num", 0, 4);
  }

  private static int sumOfRangeCounts(FacetResult r) {
    int sum = 0;
    for (LabelAndValue lv : r.labelValues) {
      sum += lv.value.intValue();
    }
    return sum;
  }

  /** Asserts the range count is right and the dimension total equals expectedTotal. */
  private static void check(String what, Facets facets, String dim, int expectedTotal)
      throws IOException {
    FacetResult r = facets.getAllChildren(dim);
    assertEquals(what + ": per-range count", 3, r.labelValues[0].value.intValue());
    assertEquals(
        what + ": FacetResult.value (documents with a counted value), result=" + r,
        expectedTotal,
        r.value.intValue());
  }

  /** Baseline without fastMatchQuery: every path reports total 3. Expected to pass. */
  public void testBaselineWithoutFastMatch() throws IOException {
    buildIndex(1);
    FacetsCollector fc = matchAll();
    check(
        "Long/LongValuesSource",
        new LongRangeFacetCounts("num", LongValuesSource.fromLongField("num"), fc, longRanges()),
        "num",
        3);
    check(
        "Long/field",
        new LongRangeFacetCounts("num", (LongValuesSource) null, fc, null, longRanges()),
        "num",
        3);
    check(
        "Double/DoubleValuesSource",
        new DoubleRangeFacetCounts(
            "dbl",
            DoubleValuesSource.fromDoubleField("dbl"),
            fc,
            new DoubleRange("low", 0, true, 2, true)),
        "dbl",
        3);
    check(
        "RangeOnRange",
        new LongRangeOnRangeFacetCounts(
            "rr",
            fc,
            RangeFieldQuery.QueryType.INTERSECTS,
            new org.apache.lucene.facet.rangeonrange.LongRange("low", 0, true, 2, true)),
        "rr",
        3);
  }

  public void testLongValuesSourceWithFastMatch() throws IOException {
    buildIndex(1);
    check(
        "Long/LongValuesSource+fastMatch",
        new LongRangeFacetCounts(
            "num", LongValuesSource.fromLongField("num"), matchAll(), fastMatch(), longRanges()),
        "num",
        3);
  }

  public void testFieldDocValuesWithFastMatch() throws IOException {
    buildIndex(1);
    check(
        "Long/field+fastMatch",
        new LongRangeFacetCounts(
            "num", (LongValuesSource) null, matchAll(), fastMatch(), longRanges()),
        "num",
        3);
  }

  /** Control: the multi-valued counting path in the same class already gives 3. */
  public void testMultiValuedPathWithFastMatchControl() throws IOException {
    buildIndex(1);
    check(
        "Long/MultiLongValuesSource+fastMatch",
        new LongRangeFacetCounts(
            "num",
            MultiLongValuesSource.fromLongField("num"),
            matchAll(),
            fastMatch(),
            longRanges()),
        "num",
        3);
  }

  /** Same data, same ranges, same fastMatchQuery: the two value-source paths must agree. */
  public void testSingleAndMultiValuedPathsAgree() throws IOException {
    buildIndex(1);
    FacetsCollector fc = matchAll();
    FacetResult single =
        new LongRangeFacetCounts(
                "num", LongValuesSource.fromLongField("num"), fc, fastMatch(), longRanges())
            .getAllChildren("num");
    FacetResult multi =
        new LongRangeFacetCounts(
                "num", MultiLongValuesSource.fromLongField("num"), fc, fastMatch(), longRanges())
            .getAllChildren("num");
    assertEquals("single=" + single + " multi=" + multi, multi.value, single.value);
  }

  public void testDoubleValuesSourceWithFastMatch() throws IOException {
    buildIndex(1);
    check(
        "Double/DoubleValuesSource+fastMatch",
        new DoubleRangeFacetCounts(
            "dbl",
            DoubleValuesSource.fromDoubleField("dbl"),
            matchAll(),
            DoublePoint.newRangeQuery("dbl", 0, 4),
            new DoubleRange("low", 0, true, 2, true)),
        "dbl",
        3);
  }

  public void testRangeOnRangeWithFastMatch() throws IOException {
    buildIndex(1);
    check(
        "RangeOnRange+fastMatch",
        new LongRangeOnRangeFacetCounts(
            "rr",
            matchAll(),
            RangeFieldQuery.QueryType.INTERSECTS,
            fastMatch(),
            new org.apache.lucene.facet.rangeonrange.LongRange("low", 0, true, 2, true)),
        "rr",
        3);
  }

  /**
   * The fastMatchQuery selects only doc 9, which is in no range: every range count is 0, so no
   * document has a counted value, yet the total is non-zero. value can never exceed the sum of the
   * per-range counts (each counted document adds at least 1 to some range).
   */
  public void testTotalExceedsSumOfRangeCounts() throws IOException {
    buildIndex(1);
    FacetResult r =
        new LongRangeFacetCounts(
                "num",
                LongValuesSource.fromLongField("num"),
                matchAll(),
                LongPoint.newRangeQuery("num", 9, 9),
                longRanges())
            .getAllChildren("num");
    assertEquals("sum of range counts, result=" + r, 0, sumOfRangeCounts(r));
    assertTrue(
        "FacetResult.value must not exceed the sum of range counts, result=" + r,
        r.value.intValue() <= sumOfRangeCounts(r));
  }

  /** Same documents, same query: the total must not depend on how docs split into segments. */
  public void testTotalDependsOnSegmentLayout() throws IOException {
    buildIndex(1);
    int oneSegment =
        new LongRangeFacetCounts(
                "num", LongValuesSource.fromLongField("num"), matchAll(), fastMatch(), longRanges())
            .getAllChildren("num")
            .value
            .intValue();
    reader.close();
    dir.close();
    reader = null;
    dir = null;
    buildIndex(2); // docs 0..4 and 5..9; the fastMatchQuery has no scorer on the second segment
    int twoSegments =
        new LongRangeFacetCounts(
                "num", LongValuesSource.fromLongField("num"), matchAll(), fastMatch(), longRanges())
            .getAllChildren("num")
            .value
            .intValue();
    assertEquals(
        "total with 1 segment=" + oneSegment + " vs 2 segments=" + twoSegments,
        twoSegments,
        oneSegment);
  }
}
