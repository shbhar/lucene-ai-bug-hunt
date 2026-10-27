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
package org.apache.lucene.document;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Distance sort from the south pole, two documents about 45 cm from the north pole with the same
 * (sloppy) distance, then a secondary sort on "tie". Ties on the distance must be broken by the
 * secondary sort, so the top-1 hit must be the first hit of the full sort.
 */
public class TestLatLonDistanceSortPoleTie extends LuceneTestCase {

  @SuppressWarnings("deprecation")
  private static final Query MATCH_ALL = new MatchAllDocsQuery();

  private static List<Integer> docs(ScoreDoc[] hits) {
    List<Integer> out = new ArrayList<>();
    for (ScoreDoc sd : hits) {
      out.add(sd.doc);
    }
    return out;
  }

  public void testTieAtAntipodeOfPoleOrigin() throws Exception {
    double originLat = -90, originLon = 146.58680372392666;
    // {lat, lon, tie}
    double[][] points = {
      {89.99999589286745, -33.41319459490478, 1}, {89.99999593477696, -33.41319216415286, 0}
    };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (double[] p : points) {
          Document doc = new Document();
          doc.add(new LatLonDocValuesField("p", p[0], p[1]));
          doc.add(new NumericDocValuesField("tie", (long) p[2]));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort =
            new Sort(
                LatLonDocValuesField.newDistanceSort("p", originLat, originLon),
                new SortField("tie", SortField.Type.INT));
        TopDocs all = searcher.search(MATCH_ALL, 2, sort);
        // both documents report the same distance, so "tie" decides: doc 1 (tie=0) first
        assertEquals(
            ((FieldDoc) all.scoreDocs[0]).fields[0], ((FieldDoc) all.scoreDocs[1]).fields[0]);
        assertEquals(List.of(1, 0), docs(all.scoreDocs));
        TopDocs top1 = searcher.search(MATCH_ALL, 1, sort);
        assertEquals("top-1 must be the head of the full sort", List.of(1), docs(top1.scoreDocs));
        // same with searchAfter paging, one hit per page
        TopDocs page2 = searcher.searchAfter(top1.scoreDocs[0], MATCH_ALL, 1, sort);
        assertEquals(List.of(0), docs(page2.scoreDocs));
      }
    }
  }
}
