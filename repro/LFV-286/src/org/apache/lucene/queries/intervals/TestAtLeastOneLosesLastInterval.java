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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAtLeastOneLosesLastInterval extends LuceneTestCase {

  public void testAtLeastOneOfTwoTerms() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new TextField("f", "a b", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IntervalsSource source = Intervals.atLeast(1, Intervals.term("a"), Intervals.term("b"));
        IntervalIterator it = source.intervals("f", reader.leaves().get(0));
        assertEquals(0, it.nextDoc());
        List<String> intervals = new ArrayList<>();
        while (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
          intervals.add("[" + it.start() + "," + it.end() + "]");
        }
        // expected: [0,0] and [1,1], as or(a, b) returns; actual: [1,1] ("b") is lost
        assertEquals(List.of("[0,0]", "[1,1]"), intervals);
      }
    }
  }
}
