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

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRepeatAlternativeUnderPhrase extends LuceneTestCase {

  public void testPhraseOverOrWithUnorderedRepeat() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(List.of(new TextField("f", "x a a c", Field.Store.NO)));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IntervalsSource a = Intervals.term("a");
        IntervalsSource repeatOrA = Intervals.or(Intervals.unordered(a, a), a);
        IntervalsSource query =
            Intervals.phrase(Intervals.term("x"), repeatOrA, Intervals.term("c"));
        // expected 1 hit: the alternative unordered(a, a) covers 'a a'; actual 0
        assertEquals(
            "phrase(x, or(unordered(a, a), a), c) on 'x a a c'",
            1,
            new IndexSearcher(r).count(new IntervalQuery("f", query)));
      }
    }
  }
}
