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
package org.apache.lucene.queries.spans;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSpanContainUnorderedLittleEnds extends LuceneTestCase {

  public void testShortLittleSpanAfterLongerOne() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new StandardAnalyzer()).setCodec(TestUtil.getDefaultCodec());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new TextField("f", "a b a", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        SpanQuery a = new SpanTermQuery(new Term("f", "a"));
        SpanQuery b = new SpanTermQuery(new Term("f", "b"));
        SpanQuery big = new SpanNearQuery(new SpanQuery[] {a, b}, 0, true);
        SpanQuery little = new SpanOrQuery(new SpanNearQuery(new SpanQuery[] {a, a}, 5, true), b);
        int containing = searcher.count(new SpanContainingQuery(big, little));
        int within = searcher.count(new SpanWithinQuery(big, little));
        // expected: big 'a b' [0,2) contains little 'b' [1,2), so both match; actual: neither does
        assertEquals("containing=1 within=1", "containing=" + containing + " within=" + within);
      }
    }
  }
}
