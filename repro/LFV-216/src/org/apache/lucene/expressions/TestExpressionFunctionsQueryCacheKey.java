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
package org.apache.lucene.expressions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.DoublePredicate;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionMatchQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestExpressionFunctionsQueryCacheKey extends LuceneTestCase {

  public static double negate(double v) {
    return -v;
  }

  public void testSameTextDifferentFunctionThroughQueryCache() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long x : new long[] {-3, -2, -1, 1, 2}) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("x", x));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        MethodType type = MethodType.methodType(double.class, double.class);
        MethodHandle abs = MethodHandles.publicLookup().findStatic(Math.class, "abs", type);
        MethodHandle negate =
            MethodHandles.publicLookup()
                .findStatic(TestExpressionFunctionsQueryCacheKey.class, "negate", type);
        SimpleBindings bindings = new SimpleBindings();
        bindings.add("x", DoubleValuesSource.fromLongField("x"));
        DoublePredicate positive = v -> v > 0;
        Query absQuery =
            new FunctionMatchQuery(
                JavascriptCompiler.compile("f(x)", Map.of("f", abs)).getDoubleValuesSource(bindings),
                positive);
        Query negateQuery =
            new FunctionMatchQuery(
                JavascriptCompiler.compile("f(x)", Map.of("f", negate))
                    .getDoubleValuesSource(bindings),
                positive);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(new LRUQueryCache(100, 1 << 20, _ -> true, Float.POSITIVE_INFINITY));
        searcher.setQueryCachingPolicy(
            new QueryCachingPolicy() {
              @Override
              public void onUse(Query query) {}

              @Override
              public boolean shouldCache(Query query) {
                return true;
              }
            });
        searcher.count(absQuery);
        // Expected: -x > 0 matches the 3 docs with x < 0; actual: the cache returns abs(x) > 0's 5 matches.
        assertEquals(3, searcher.count(negateQuery));
      }
    }
  }
}
