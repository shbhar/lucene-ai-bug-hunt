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
package org.apache.lucene.search.grouping;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestGroupingSearchCacheLimit extends LuceneTestCase {

  public void testCacheLimitAppliesToWholeSearch() throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 6; i++) {
        Document doc = new Document();
        doc.add(new SortedDocValuesField("group", new BytesRef("g")));
        w.addDocument(doc);
        w.flush(); // 6 one-doc segments; default slicing gives 2 slices (5 + 1 docs)
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        AtomicInteger queryRuns = new AtomicInteger();
        IndexSearcher searcher =
            new IndexSearcher(reader, Runnable::run) {
              @Override
              public Weight createWeight(Query query, ScoreMode scoreMode, float boost)
                  throws IOException {
                queryRuns.incrementAndGet();
                return super.createWeight(query, scoreMode, boost);
              }
            };
        new GroupingSearch("group")
            .setCaching(5, true)
            .search(searcher, new MatchAllDocsQuery(), 0, 10);
        assertEquals(
            "6 docs match, over the cache limit of 5: the cache must be purged and the query re-run",
            2,
            queryRuns.get());
      }
    }
  }
}
