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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpanNotNegativePost extends LuceneTestCase {

  public void testNegativePostKeepsSpansAfterLastExclude() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document doc = new Document();
      doc.add(new TextField("f", "b a a c", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SpanQuery q =
            new SpanNotQuery(
                new SpanTermQuery(new Term("f", "a")), new SpanTermQuery(new Term("f", "b")), 0, -1);
        Spans spans =
            q.createWeight(new IndexSearcher(reader), ScoreMode.COMPLETE_NO_SCORES, 1f)
                .getSpans(reader.leaves().get(0), SpanWeight.Postings.POSITIONS);
        List<String> actual = new ArrayList<>();
        spans.nextDoc();
        while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
          actual.add("[" + spans.startPosition() + "," + spans.endPosition() + ")");
        }
        // "b" at 0 overlaps neither "a": expected [1,2) and [2,3) (as with post 0), actual [1,2)
        assertEquals(List.of("[1,2)", "[2,3)"), actual);
      }
    }
  }
}
