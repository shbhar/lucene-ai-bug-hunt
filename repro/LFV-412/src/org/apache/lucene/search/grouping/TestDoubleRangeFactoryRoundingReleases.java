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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * DoubleRangeFactory.getRange picks the bucket with Math.floor((value - min) / width) but labels it
 * [min + bucket * width, min + bucket * width + width). Both are rounded double computations that
 * disagree near the boundaries, so with an ordinary decimal width a value gets a range that does not
 * contain it: DoubleRangeFactory(0, 0.1, 10).getRange(0.6) is DoubleRange(0.5, 0.6), and the next
 * bucket's label starts at 0.6000000000000001, so no returned label contains 0.6.
 */
public class TestDoubleRangeFactoryRoundingReleases extends LuceneTestCase {

  private static boolean contains(DoubleRange r, double v) {
    return r.min <= v && v < r.max;
  }

  public void testRangeContainsTheValue() {
    DoubleRangeFactory f = new DoubleRangeFactory(0, 0.1, 10);
    for (double v : new double[] {0.6, 1.7, 4.1}) {
      DoubleRange r = f.getRange(v, null);
      assertTrue(r + " does not contain " + v, contains(r, v));
    }
  }

  public void testGroupLabelSelectsItsDocuments() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (double v : new double[] {0.55, 0.6, 0.65}) {
          Document d = new Document();
          d.add(new DoublePoint("double", v));
          d.add(new DoubleDocValuesField("double", v));
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        GroupingSearch gs =
            new GroupingSearch(new DoubleRangeGroupSelector(
                        DoubleValuesSource.fromDoubleField("double"),
                        new DoubleRangeFactory(0, 0.1, 10)));
        gs.setGroupDocsLimit(10);
        TopGroups<DoubleRange> tg = gs.search(searcher, MatchAllDocsQuery.INSTANCE, 0, 10);
        for (GroupDocs<DoubleRange> g : tg.groups) {
          DoubleRange r = g.groupValue();
          Query filter = DoublePoint.newRangeQuery("double", r.min, Math.nextDown(r.max));
          int inLabel =
              searcher.count(
                  new BooleanQuery.Builder()
                      .add(MatchAllDocsQuery.INSTANCE, BooleanClause.Occur.MUST)
                      .add(filter, BooleanClause.Occur.FILTER)
                      .build());
          assertEquals("group " + r, g.totalHits().value(), inLabel);
        }
      }
    }
  }
}
