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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDocAndScoreQueryRepeatedDoc extends LuceneTestCase {

  public void testRepeatedFirstDocOfSegment() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
        w.commit();
        w.addDocument(new Document());
        w.addDocument(new Document());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        ScoreDoc[] topK = {new ScoreDoc(1, 2f), new ScoreDoc(1, 2f), new ScoreDoc(2, 1f)};
        Query q =
            DocAndScoreQuery.createDocAndScoreQuery(
                r, new TopDocs(new TotalHits(3, TotalHits.Relation.EQUAL_TO), topK), 0);
        Weight weight = new IndexSearcher(r).createWeight(q, ScoreMode.COMPLETE, 1f);
        Scorer seg0 = weight.scorer(r.leaves().get(0));
        // expected: segment 0 (doc 0 only) matches nothing; actual: it returns doc 1 == its maxDoc
        int first = seg0 == null ? DocIdSetIterator.NO_MORE_DOCS : seg0.iterator().nextDoc();
        assertEquals("first doc of segment 0", DocIdSetIterator.NO_MORE_DOCS, first);
      }
    }
  }
}
