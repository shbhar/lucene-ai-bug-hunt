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
package org.apache.lucene.sandbox.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPhraseWildcardQueryCacheable extends LuceneTestCase {

  public void testCachedCountAfterReopen() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random()))
                .setCodec(TestUtil.getDefaultCodec())
                .setMergePolicy(NoMergePolicy.INSTANCE));
    Query query =
        new PhraseWildcardQuery.Builder("f", 1)
            .addMultiTerm(new PrefixQuery(new Term("f", "a")))
            .addTerm(new Term("f", "x"))
            .build();
    LRUQueryCache cache = new LRUQueryCache(100, 1 << 20, ctx -> true, Float.POSITIVE_INFINITY);
    QueryCachingPolicy always =
        new QueryCachingPolicy() {
          @Override
          public void onUse(Query q) {}

          @Override
          public boolean shouldCache(Query q) {
            return true;
          }
        };

    Document doc = new Document();
    doc.add(new TextField("f", "aa x c", Field.Store.NO));
    w.addDocument(doc);
    w.commit();
    DirectoryReader r1 = DirectoryReader.open(dir);
    IndexSearcher s1 = new IndexSearcher(r1);
    s1.setQueryCache(cache);
    s1.setQueryCachingPolicy(always);
    assertEquals(1, s1.count(query));

    doc = new Document();
    doc.add(new TextField("f", "ab x", Field.Store.NO));
    w.addDocument(doc);
    w.commit();
    DirectoryReader r2 = DirectoryReader.openIfChanged(r1);
    IndexSearcher uncached = new IndexSearcher(r2);
    uncached.setQueryCache(null);
    IndexSearcher cached = new IndexSearcher(r2);
    cached.setQueryCache(cache);
    cached.setQueryCachingPolicy(always);
    // expected: cached count == uncached count (1, the one expansion goes to the smaller segment); actual: 2
    assertEquals(uncached.count(query), cached.count(query));

    r1.close();
    r2.close();
    w.close();
    dir.close();
  }
}
