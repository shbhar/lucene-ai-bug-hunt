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
package zeroconfig;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.document.LateInteractionField;
import org.apache.lucene.queries.function.FunctionMatchQuery;
import org.apache.lucene.search.LateInteractionFloatValuesSource;

/**
 * Zero-configuration variant: default IndexWriterConfig, default IndexSearcher query cache and
 * caching policy (no cache is installed explicitly). Exit code 1 if the cached result is stale.
 */
public class ZeroConfigLateInteraction {
  public static void main(String[] args) throws Exception {
    System.out.println("default query cache: " + IndexSearcher.getDefaultQueryCache());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 12_000; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new LateInteractionField("mv", i < 5 ? new float[][] {{1f, 0f}} : new float[][] {{0f, 1f}}));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      Query query = new FunctionMatchQuery(new LateInteractionFloatValuesSource("mv", new float[][] {{1f, 0f}}), score -> score > 0.75);
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        int before = 0;
        for (int k = 0; k < 6; k++) { // the default policy caches a filter after a few uses
          before = new IndexSearcher(r1).count(query);
        }
        w.updateBinaryDocValue(new Term("id", "0"), "mv", LateInteractionField.encode(new float[][] {{0f, 1f}}));
        try (DirectoryReader r2 = DirectoryReader.openIfChanged(r1, w)) {
          int cached = new IndexSearcher(r2).count(query);
          IndexSearcher uncachedSearcher = new IndexSearcher(r2);
          uncachedSearcher.setQueryCache(null);
          int uncached = uncachedSearcher.count(query);
          System.out.println("before update: " + before + ", after update: default searcher " + cached + ", uncached " + uncached);
          if (cached != uncached) {
            System.out.println("STALE: the default query cache served the pre-update result");
            System.exit(1);
          }
          System.out.println("OK");
        }
      }
    }
  }
}
