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
package org.apache.lucene.search.similarities;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLMDirichletMuZero extends LuceneTestCase {

  public void testMuZeroScoresBestMatchAboveZero() throws Exception {
    LMDirichletSimilarity sim = new LMDirichletSimilarity(0f);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()).setSimilarity(sim))) {
        for (String text : new String[] {"a", "a b c d", "b c"}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        TopDocs td = searcher.search(new TermQuery(new Term("body", "a")), 10);
        // expected: doc "a" (tf/dl = 1, above the collection model P = 3/8) scores > 0; actual: 0.0
        assertTrue(
            "doc " + td.scoreDocs[0].doc + " scores " + td.scoreDocs[0].score + ", expected > 0",
            td.scoreDocs[0].score > 0f);
      }
    }
  }
}
