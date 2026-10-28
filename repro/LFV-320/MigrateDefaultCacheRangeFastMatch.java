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
package migrate;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * On main the query cache is off by default; this re-enables it exactly as MIGRATE.md says
 * ("Query caching is now disabled by default"), then uses only defaults otherwise. Fast match is
 * a selective postings clause ANDed with a doc-values restriction. Exit 1 if stale.
 */
public class MigrateDefaultCacheRangeFastMatch {
  public static void main(String[] args) throws Exception {
    int maxCachedQueries = 1_000;
    long maxRamBytesUsed = 50 * 1024 * 1024; // 50MB
    IndexSearcher.setDefaultQueryCache(new LRUQueryCache(maxCachedQueries, maxRamBytesUsed));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 12_000; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new StringField("category", i < 3_000 ? "shoes" : "hats", Field.Store.NO));
        doc.add(new NumericDocValuesField("inStock", i < 5 ? 1 : 0));
        doc.add(new NumericDocValuesField("price", 10));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      Query fastMatch =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("category", "shoes")), Occur.MUST)
              .add(NumericDocValuesField.newSlowExactQuery("inStock", 1), Occur.FILTER)
              .build();
      Query query =
          new LongRange("r", 0, true, 100, true)
              .getQuery(fastMatch, LongValuesSource.fromLongField("price"));
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        int before = 0;
        for (int k = 0; k < 6; k++) {
          before = new IndexSearcher(r1).count(query);
        }
        w.updateNumericDocValue(new Term("id", "0"), "inStock", 0);
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          int cached = new IndexSearcher(r2).count(query);
          IndexSearcher uncachedSearcher = new IndexSearcher(r2);
          uncachedSearcher.setQueryCache(null);
          int uncached = uncachedSearcher.count(query);
          System.out.println(
              "before update: " + before + ", after update: default searcher " + cached
                  + ", uncached " + uncached);
          if (cached != uncached) {
            System.out.println("STALE: the re-enabled default query cache served the pre-update result");
            System.exit(1);
          }
          System.out.println("OK");
        }
      }
    }
  }
}
