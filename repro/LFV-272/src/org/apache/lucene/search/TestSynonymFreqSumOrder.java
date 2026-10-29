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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSynonymFreqSumOrder extends LuceneTestCase {

  public void testExplainEqualsHitScore() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new StandardAnalyzer()).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 4; i++) {
          Document doc = new Document();
          doc.add(new TextField("f", "a b c", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q =
            new SynonymQuery.Builder("f")
                .addTerm(new Term("f", "a"), 0.2f)
                .addTerm(new Term("f", "b"), 0.2f)
                .addTerm(new Term("f", "c"), 0.3f)
                .build();
        for (ScoreDoc hit : searcher.search(q, 10).scoreDocs) {
          // explain() must report the hit's score; on main they differ by 1 ulp for docs 1 and 2
          float explained = searcher.explain(q, hit.doc).getValue().floatValue();
          assertEquals("doc " + hit.doc + " hit score vs explain()", hit.score, explained, 0f);
        }
      }
    }
  }
}
