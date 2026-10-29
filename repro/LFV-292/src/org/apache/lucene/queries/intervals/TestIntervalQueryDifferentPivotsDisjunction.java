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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIntervalQueryDifferentPivotsDisjunction extends LuceneTestCase {

  public void testShouldClausesWithDifferentPivotsScoreAsSum() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new TextField("f", "a", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q1 = new IntervalQuery("f", Intervals.term("a"), 1f);
        Query q2 = new IntervalQuery("f", Intervals.term("a"), 100f);
        float s1 = searcher.search(q1, 1).scoreDocs[0].score;
        float s2 = searcher.search(q2, 1).scoreDocs[0].score;
        Query both = new BooleanQuery.Builder().add(q1, Occur.SHOULD).add(q2, Occur.SHOULD).build();
        float actual = searcher.search(both, 1).scoreDocs[0].score;
        // expected s1 + s2 (0.5 + 0.0099); actual 2 * s1 = 1.0, because q2 equals q1 and is merged
        assertEquals("score of SHOULD(pivot 1, pivot 100)", s1 + s2, actual, 1e-6f);
      }
    }
  }
}
