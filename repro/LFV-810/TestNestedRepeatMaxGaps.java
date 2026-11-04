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
package org.apache.lucene.queries.intervals;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNestedRepeatMaxGaps extends LuceneTestCase {

  public void testMaxGapsZeroOverNestedRepeat() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "a x a b", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IntervalsSource a = Intervals.term("a");
        IntervalsSource pair = Intervals.unordered(a, a); // [0,2] in 'a x a b'
        IntervalQuery q =
            new IntervalQuery("f", Intervals.maxgaps(0, Intervals.ordered(pair, Intervals.term("b"))));
        // b (position 3) directly follows the pair [0,2]: 0 gaps between the two parts, expected 1 hit
        assertEquals(q + " on 'a x a b'", 1, new IndexSearcher(reader).count(q));
      }
    }
  }
}
