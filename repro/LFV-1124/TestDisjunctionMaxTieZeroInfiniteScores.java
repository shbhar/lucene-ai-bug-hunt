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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDisjunctionMaxTieZeroInfiniteScores extends LuceneTestCase {

  public void testTwoInfiniteDisjunctsScoreTheMax() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new FeatureField("f", "a", 1e38f));
        doc.add(new FeatureField("f", "b", 1e38f));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q =
            new DisjunctionMaxQuery(
                List.of(
                    FeatureField.newLinearQuery("f", "a", 64f),
                    FeatureField.newLinearQuery("f", "b", 64f)),
                0f);
        TopDocs hits =
            new IndexSearcher(reader)
                .search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
        // expected max(64*1e38, 64*1e38) = +Infinity, actual NaN (Infinity + Infinity * 0)
        assertEquals(Float.POSITIVE_INFINITY, hits.scoreDocs[0].score, 0f);
      }
    }
  }
}
