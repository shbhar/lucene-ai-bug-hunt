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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRescoreTopNQueryScoresSource extends LuceneTestCase {

  public void testScoresSourceSeesInnerScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String body : new String[] {"a b c d", "a"}) {
          Document doc = new Document();
          doc.add(new TextField("body", body, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query inner = new TermQuery(new Term("body", "a"));
        ScoreDoc best = searcher.search(inner, 1).scoreDocs[0];
        ScoreDoc rescored =
            searcher.search(new RescoreTopNQuery(inner, DoubleValuesSource.SCORES, 1), 1)
                .scoreDocs[0];
        // expected: the inner query's best hit and score (doc 1, BM25 > 0); actual: doc 0, score 0
        assertEquals(best.doc + ":" + best.score, rescored.doc + ":" + rescored.score);
      }
    }
  }
}
