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
package org.apache.lucene.search.grouping;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LongRangeFactory/DoubleRangeFactory javadoc: "all ranges between min and max are this wide, with
 * the exception of the final range which may be up to this width". With max not on a width
 * boundary the final in-range bucket should end at max; it ends at lo + width instead and overlaps
 * the bucket of values >= max.
 */
public class TestRangeFactoryFinalRange extends LuceneTestCase {

  public void testLongRangeFactory() {
    LongRangeFactory factory = new LongRangeFactory(10, 10, 25);
    assertEquals(new LongRange(25, Long.MAX_VALUE), factory.getRange(27, null));
    assertEquals(new LongRange(20, 25), factory.getRange(22, null)); // actual: LongRange(20, 30)
  }

  public void testDoubleRangeFactory() {
    DoubleRangeFactory factory = new DoubleRangeFactory(10, 10, 25);
    assertEquals(new DoubleRange(25, Double.MAX_VALUE), factory.getRange(27, null));
    assertEquals(new DoubleRange(20, 25), factory.getRange(22, null)); // actual: DoubleRange(20.0, 30.0)
  }

  /** End to end: the reported group ranges overlap, and a range query over a group's range finds other groups' docs. */
  public void testGroupRangesDoNotOverlap() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long v : new long[] {22, 27}) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("v", v));
          doc.add(new LongPoint("v", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        LongRangeFactory factory = new LongRangeFactory(10, 10, 25);
        GroupingSearch gs =
            new GroupingSearch(
                () -> new LongRangeGroupSelector(LongValuesSource.fromLongField("v"), factory));
        TopGroups<LongRange> groups = gs.search(s, MatchAllDocsQuery.INSTANCE, 0, 10);
        assertEquals(2, groups.groups.length);
        for (GroupDocs<LongRange> g : groups.groups) {
          LongRange range = g.groupValue();
          // the documents whose value lies in the group's [min, max) are exactly the group's documents
          assertEquals(
              "documents in " + range,
              g.totalHits().value(),
              s.count(LongPoint.newRangeQuery("v", range.min, range.max - 1)));
        }
      }
    }
  }
}
