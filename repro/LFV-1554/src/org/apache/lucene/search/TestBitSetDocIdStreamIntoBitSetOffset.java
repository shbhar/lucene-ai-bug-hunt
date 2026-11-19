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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;

public class TestBitSetDocIdStreamIntoBitSetOffset extends LuceneTestCase {

  // Expected: docs 1 and 2 are loaded as bits 0 and 1. Actual: IndexOutOfBoundsException.
  public void testIntoBitSetWithOffset() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String value : new String[] {"a", "a", "b"}) {
        Document doc = new Document();
        doc.add(new StringField("f", value, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query query =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("f", "a")), Occur.SHOULD)
                .add(new TermQuery(new Term("f", "b")), Occur.SHOULD)
                .build();
        Weight weight =
            searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
        BulkScorer scorer = weight.bulkScorer(reader.leaves().get(0));

        FixedBitSet bits = new FixedBitSet(2);
        scorer.score(
            new LeafCollector() {
              @Override
              public void setScorer(Scorable scorer) {}

              @Override
              public void collect(int doc) {
                bits.set(doc - 1);
              }

              @Override
              public void collect(DocIdStream stream) throws IOException {
                stream.intoBitSet(3, bits, 1);
              }
            },
            null,
            1,
            3);

        assertEquals(2, bits.cardinality());
      }
    }
  }
}
