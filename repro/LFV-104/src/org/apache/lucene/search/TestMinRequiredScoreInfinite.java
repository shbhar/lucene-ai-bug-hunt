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
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMinRequiredScoreInfinite extends LuceneTestCase {

  public void testInfiniteScoreAfterMaxValueBottom() throws Exception {
    int lateDoc = 1001;
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i <= lateDoc; i++) {
          Document doc = new Document();
          doc.add(new FeatureField("f", "a", i == 0 ? 0x1p127f : i == lateDoc ? 0x1.8p127f : 1f));
          doc.add(new FeatureField("f", "b", 1f));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q =
            new BooleanQuery.Builder()
                .add(FeatureField.newLinearQuery("f", "a", Math.nextDown(2f)), Occur.MUST)
                .add(FeatureField.newLinearQuery("f", "b", 1f), Occur.MUST)
                .build();
        ScoreDoc top = new IndexSearcher(reader).search(q, 1).scoreDocs[0];
        // expected: doc 1001 scoring +Infinity; actual: doc 0 scoring Float.MAX_VALUE
        assertEquals("top hit " + top.doc + " score " + top.score, lateDoc, top.doc);
      }
    }
  }
}
