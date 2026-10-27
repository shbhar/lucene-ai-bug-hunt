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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Same three documents, same query, same fastMatchQuery and same range; only the segment layout
 * differs. If FacetResult.value were meant to be "all collected hits", it would be 3 in both
 * layouts; if it were meant to be "documents in some range", it would be 1 in both layouts.
 */
public class TestRangeFacetTotalSegmentDependence extends LuceneTestCase {

  private static Document doc(long v) {
    Document d = new Document();
    d.add(new NumericDocValuesField("v", v));
    // tag only the document that falls into the facet range [0, 5]; the other documents have no
    // "tag" field, so a segment that holds only them has no "tag" terms at all
    if (v <= 5) {
      d.add(new StringField("tag", "in", Field.Store.NO));
    }
    return d;
  }

  private static int total(Directory dir, boolean splitSegments) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc(0));
      if (splitSegments) {
        w.commit(); // doc 0 alone in segment 1; docs 10 and 20 in segment 2
      }
      w.addDocument(doc(10));
      w.addDocument(doc(20));
      w.commit();
    }
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(splitSegments ? 2 : 1, r.leaves().size());
      IndexSearcher s = new IndexSearcher(r);
      @SuppressWarnings("deprecation")
      FacetsCollector fc = s.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      Query fastMatch = new TermQuery(new Term("tag", "in"));
      LongRange range = new LongRange("r", 0, true, 5, true);
      FacetResult res =
          new LongRangeFacetCounts("v", (org.apache.lucene.search.LongValuesSource) null, fc, fastMatch, range)
              .getAllChildren("v");
      assertEquals("per-range count", 1, res.labelValues[0].value.intValue());
      return res.value.intValue();
    }
  }

  public void testTotalDependsOnSegmentLayout() throws Exception {
    int oneSegment;
    int twoSegments;
    try (Directory d1 = newDirectory()) {
      oneSegment = total(d1, false);
    }
    try (Directory d2 = newDirectory()) {
      twoSegments = total(d2, true);
    }
    System.out.println("PROBE total oneSegment=" + oneSegment + " twoSegments=" + twoSegments);
    assertEquals("FacetResult.value must not depend on segment layout", oneSegment, twoSegments);
  }
}
