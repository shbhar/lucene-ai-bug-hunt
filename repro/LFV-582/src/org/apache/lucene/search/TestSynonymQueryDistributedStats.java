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
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSynonymQueryDistributedStats extends LuceneTestCase {

  public void testSynonymOnlyOnOtherShard() throws IOException {
    try (Directory d0 = new ByteBuffersDirectory();
        Directory d1 = new ByteBuffersDirectory()) {
      index(d0, "quick");
      index(d1, "fast", "fast");
      try (DirectoryReader r0 = DirectoryReader.open(d0);
          DirectoryReader r1 = DirectoryReader.open(d1)) {
        IndexSearcher collection = new IndexSearcher(new MultiReader(r0, r1));
        IndexSearcher shard0 =
            new IndexSearcher(r0) {
              @Override
              public TermStats termStats(Term term, int docFreq, long totalTermFreq)
                  throws IOException {
                TermStates ts = TermStates.build(collection, term, true);
                return new TermStats(term.bytes(), ts.docFreq(), ts.totalTermFreq());
              }

              @Override
              public FieldStats fieldStats(String field) throws IOException {
                return collection.fieldStats(field);
              }
            };
        Query q =
            new SynonymQuery.Builder("f")
                .addTerm(new Term("f", "quick"))
                .addTerm(new Term("f", "fast"))
                .build();
        // expected: doc 0 scores as in the collection (all 3 docs tie there); actual: higher
        assertEquals(
            "score of doc 0 on shard 0 vs. in the whole collection",
            collection.search(q, 1).scoreDocs[0].score,
            shard0.search(q, 1).scoreDocs[0].score,
            0f);
      }
    }
  }

  private static void index(Directory dir, String... values) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String value : values) {
        Document doc = new Document();
        doc.add(new TextField("f", value, Field.Store.NO));
        w.addDocument(doc);
      }
    }
  }
}
