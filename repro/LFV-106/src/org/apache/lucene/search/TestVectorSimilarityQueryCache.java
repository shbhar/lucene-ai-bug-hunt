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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestVectorSimilarityQueryCache extends LuceneTestCase {

  public void testOlderReaderAfterNewerReader() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (String id : new String[] {"0", "1"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        doc.add(new KnnFloatVectorField("v", new float[] {1, 0}));
        w.addDocument(doc);
      }
      try (DirectoryReader older = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "0"));
        try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
          QueryCache cache = new LRUQueryCache(1000, 1 << 24, _ -> true, Float.POSITIVE_INFINITY);
          Query query =
              new FloatVectorSimilarityQuery.Adaptive("v", new float[] {1, 0}, 0f, 1f, null);
          assertEquals(1, searcher(newer, cache).count(query));
          // doc 0 is live in the older reader (same segment core): expected 2, actual 1
          assertEquals(2, searcher(older, cache).count(query));
        }
      }
    }
  }

  private static IndexSearcher searcher(IndexReader reader, QueryCache cache) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(
        new QueryCachingPolicy() {
          @Override
          public void onUse(Query query) {}

          @Override
          public boolean shouldCache(Query query) {
            return true;
          }
        });
    return searcher;
  }
}
