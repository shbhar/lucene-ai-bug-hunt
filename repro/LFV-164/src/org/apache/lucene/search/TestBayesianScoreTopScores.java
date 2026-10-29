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

public class TestBayesianScoreTopScores extends LuceneTestCase {

  public void testTopHitIsNotPruned() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      addDocs(w, 3900, "filler filler");
      addDocs(w, 1100, "a filler filler filler");
      addDocs(w, 1, "a a a");
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q = new BayesianScoreQuery(new TermQuery(new Term("body", "a")), 1f, 5f, 1e-6f);
        ScoreDoc best =
            searcher.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE)).scoreDocs[0];
        ScoreDoc top = searcher.search(q, 1).scoreDocs[0];
        // expected: search(q, 1) returns the exhaustive top hit (doc 5000); actual: doc 3900
        assertEquals("exhaustive " + best + ", search(q, 1) " + top, best.doc, top.doc);
      }
    }
  }

  private static void addDocs(IndexWriter w, int count, String body) throws IOException {
    for (int i = 0; i < count; i++) {
      Document doc = new Document();
      doc.add(new TextField("body", body, Field.Store.NO));
      w.addDocument(doc);
    }
  }
}
