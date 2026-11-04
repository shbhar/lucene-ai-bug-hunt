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
package org.apache.lucene.queries.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.queries.function.valuesource.ScaleFloatFunction;
import org.apache.lucene.queries.function.valuesource.SumFloatFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NamedThreadFactory;

public class TestScaleFunctionWithExecutor extends LuceneTestCase {

  public void testExecutorSearchMatchesSequentialSearch() throws Exception {
    Random r = new Random(7);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int segment = 0; segment < 32; segment++) { // 32 segments: 7 slices
          for (int d = 0; d < 50; d++) {
            StringBuilder body = new StringBuilder();
            for (int k = 0, n = 1 + r.nextInt(6); k < n; k++) {
              body.append(" w").append(r.nextInt(24));
            }
            Document doc = new Document();
            doc.add(new TextField("body", body.toString(), Field.Store.NO));
            doc.add(new NumericDocValuesField("num", r.nextInt(1000)));
            w.addDocument(doc);
          }
          w.flush();
        }
      }
      // sum(scale(num, 0, 1), query(body:w0), ..., query(body:w19)): after createWeight the shared
      // context map holds 21 entries ("searcher" and 20 query weights), so the entry scale() puts
      // during the search is the 22nd, and an IdentityHashMap grows when its 22nd entry is added
      List<ValueSource> sources = new ArrayList<>();
      sources.add(new ScaleFloatFunction(new LongFieldSource("num"), 0, 1));
      for (int i = 0; i < 20; i++) {
        sources.add(new QueryValueSource(new TermQuery(new Term("body", "w" + i)), 0f));
      }
      Query query = new FunctionQuery(new SumFloatFunction(sources.toArray(new ValueSource[0])));

      ExecutorService executor = Executors.newFixedThreadPool(8, new NamedThreadFactory("scale"));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher sequential = new IndexSearcher(reader);
        IndexSearcher concurrent = new IndexSearcher(reader, executor);
        sequential.setSimilarity(new BM25Similarity(1.8f, 0.3f)); // not the default k1 and b
        concurrent.setSimilarity(new BM25Similarity(1.8f, 0.3f));
        String expected = topHits(sequential, query);
        for (int i = 0; i < 1000; i++) {
          assertEquals("search " + i, expected, topHits(concurrent, query));
        }
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
      }
    }
  }

  private static String topHits(IndexSearcher searcher, Query query) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : searcher.search(query, 10).scoreDocs) {
      sb.append(sd.doc).append('=').append(sd.score).append(' ');
    }
    return sb.toString();
  }
}
