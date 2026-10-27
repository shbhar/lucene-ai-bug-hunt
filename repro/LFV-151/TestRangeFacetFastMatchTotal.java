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
package org.apache.lucene.facet.range;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoubleRangeDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongRangeDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.RangeFieldQuery.QueryType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.MultiLongValuesSource;
import org.apache.lucene.facet.rangeonrange.DoubleRangeOnRangeFacetCounts;
import org.apache.lucene.facet.rangeonrange.LongRangeOnRangeFacetCounts;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Three documents with the values 0, 10 and 20 and one facet range [0, 5]: one document falls in
 * the range, so {@link FacetResult#value} is 1. A fastMatchQuery that matches only that document
 * skips the two documents that fall in no range anyway, so it must not change the total.
 */
public class TestRangeFacetFastMatchTotal extends LuceneTestCase {

  /** Matches only the document with value 0, the one inside the facet range. */
  private static final Query FAST_MATCH = new TermQuery(new Term("tag", "in"));

  private static Document doc(long v) {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("long", v));
    doc.add(new DoubleDocValuesField("double", v));
    doc.add(new LongRangeDocValuesField("longRange", new long[] {v}, new long[] {v + 1}));
    doc.add(new DoubleRangeDocValuesField("doubleRange", new double[] {v}, new double[] {v + 1}));
    if (v <= 5) {
      doc.add(new StringField("tag", "in", Field.Store.NO));
    }
    return doc;
  }

  /** Indexes 0, 10 and 20 in one segment, or 0 in one segment and 10, 20 in a second one. */
  private static DirectoryReader index(Directory dir, boolean twoSegments) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc(0));
      if (twoSegments) {
        w.commit();
      }
      w.addDocument(doc(10));
      w.addDocument(doc(20));
    }
    DirectoryReader reader = DirectoryReader.open(dir);
    assertEquals(twoSegments ? 2 : 1, reader.leaves().size());
    return reader;
  }

  private static int total(Facets counts, String dim) throws IOException {
    FacetResult result = counts.getAllChildren(dim);
    assertEquals("documents in [0, 5]", 1, result.labelValues[0].value.intValue());
    return result.value.intValue();
  }

  /** FacetResult.value of seven range counters over all documents, each with the range [0, 5]. */
  private static List<Integer> totals(DirectoryReader reader, Query fastMatch) throws IOException {
    FacetsCollector fc =
        new IndexSearcher(reader).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    LongRange longs = new LongRange("[0, 5]", 0, true, 5, true);
    DoubleRange doubles = new DoubleRange("[0, 5]", 0, true, 5, true);
    List<Integer> totals = new ArrayList<>();
    totals.add(
        total(
            new LongRangeFacetCounts(
                "long", LongValuesSource.fromLongField("long"), fc, fastMatch, longs),
            "long"));
    totals.add( // field doc values
        total(
            new LongRangeFacetCounts("long", (LongValuesSource) null, fc, fastMatch, longs),
            "long"));
    totals.add(
        total(
            new DoubleRangeFacetCounts(
                "double", DoubleValuesSource.fromDoubleField("double"), fc, fastMatch, doubles),
            "double"));
    totals.add( // field doc values
        total(
            new DoubleRangeFacetCounts("double", (DoubleValuesSource) null, fc, fastMatch, doubles),
            "double"));
    totals.add(
        total(
            new LongRangeOnRangeFacetCounts(
                "longRange",
                fc,
                QueryType.INTERSECTS,
                fastMatch,
                new org.apache.lucene.facet.rangeonrange.LongRange("[0, 5]", 0, true, 5, true)),
            "longRange"));
    totals.add(
        total(
            new DoubleRangeOnRangeFacetCounts(
                "doubleRange",
                fc,
                QueryType.INTERSECTS,
                fastMatch,
                new org.apache.lucene.facet.rangeonrange.DoubleRange("[0, 5]", 0, true, 5, true)),
            "doubleRange"));
    totals.add( // control: the multi-valued path counts each checked document
        total(
            new LongRangeFacetCounts(
                "long", MultiLongValuesSource.fromLongField("long"), fc, fastMatch, longs),
            "long"));
    return totals;
  }

  public void testFastMatchDoesNotChangeTotal() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, false)) {
      List<Integer> withoutFastMatch = totals(reader, null);
      assertEquals(List.of(1, 1, 1, 1, 1, 1, 1), withoutFastMatch);
      assertEquals(
          "FacetResult.value with a fastMatchQuery that skips only documents in no range",
          withoutFastMatch,
          totals(reader, FAST_MATCH));
    }
  }

  public void testTotalDoesNotDependOnSegmentLayout() throws IOException {
    List<Integer> oneSegment;
    List<Integer> twoSegments;
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, false)) {
      oneSegment = totals(reader, FAST_MATCH);
    }
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, true)) {
      twoSegments = totals(reader, FAST_MATCH);
    }
    assertEquals(
        "same documents and query; expected: two segments, actual: one segment",
        twoSegments,
        oneSegment);
  }
}
