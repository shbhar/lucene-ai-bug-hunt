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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.AxiomaticF2LOG;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCombinedFieldQueryNaNMaxScore extends LuceneTestCase {

  public void testPrunedTopHit() throws Exception {
    Similarity sim = new AxiomaticF2LOG();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setSimilarity(sim).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 256; i++) {
          Document doc = new Document();
          doc.add(new TextField("c", "y", Field.Store.NO));
          if (i == 0) {
            doc.add(new TextField("a", "x", Field.Store.NO));
          }
          w.addDocument(doc);
        }
        Document best = new Document();
        best.add(new TextField("a", "x x x", Field.Store.NO));
        w.addDocument(best);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Query query =
            new BooleanQuery.Builder()
                .add(
                    new CombinedFieldQuery.Builder("x").addField("a").build(),
                    BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("c", "y")), BooleanClause.Occur.SHOULD)
                .build();
        TopDocs exhaustive =
            searcher.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
        TopDocs pruned = searcher.search(query, new TopScoreDocCollectorManager(1, 1));
        // expected: pruning keeps the top hit (doc 256, a:x only); actual: doc 0, getMaxScore is NaN
        assertEquals(
            "top hit with pruning", exhaustive.scoreDocs[0].doc, pruned.scoreDocs[0].doc);
      }
    }
  }
}
