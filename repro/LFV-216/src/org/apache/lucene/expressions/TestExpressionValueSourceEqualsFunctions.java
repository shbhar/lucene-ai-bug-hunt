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
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoublePredicate;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queries.function.FunctionMatchQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.junit.jupiter.api.Test;

/**
 * ExpressionValueSource.equals/hashCode compare the expression's source text and the bound
 * variables, but not the functions the text was compiled with. Two value sources that compute
 * different values are therefore equal, and so are queries built on them, which lets the query
 * cache return one query's matches for the other.
 */
public class TestExpressionValueSourceEqualsFunctions extends LuceneTestCaseJupiter {

  public static double negate(double v) {
    return -v;
  }

  private static Map<String, MethodHandle> withF(String target) throws Exception {
    Map<String, MethodHandle> functions = new HashMap<>(JavascriptCompiler.DEFAULT_FUNCTIONS);
    MethodType mt = MethodType.methodType(double.class, double.class);
    functions.put(
        "f",
        target.equals("abs")
            ? MethodHandles.publicLookup().findStatic(Math.class, "abs", mt)
            : MethodHandles.publicLookup()
                .findStatic(TestExpressionValueSourceEqualsFunctions.class, "negate", mt));
    return functions;
  }

  @Test
  public void testDifferentFunctionsAreNotEqual() throws Exception {
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("x", DoubleValuesSource.fromLongField("x"));
    DoubleValuesSource abs =
        JavascriptCompiler.compile("f(x)", withF("abs")).getDoubleValuesSource(bindings);
    DoubleValuesSource neg =
        JavascriptCompiler.compile("f(x)", withF("negate")).getDoubleValuesSource(bindings);
    assertFalse("f=abs and f=negate value sources must not be equal", abs.equals(neg));
  }

  @Test
  public void testQueryCacheReturnsOtherQuerysMatches() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (long v : new long[] {-3, -2, -1, 1, 2}) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("x", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SimpleBindings bindings = new SimpleBindings();
        bindings.add("x", DoubleValuesSource.fromLongField("x"));
        DoublePredicate positive = v -> v > 0;
        Query absPositive =
            new FunctionMatchQuery(
                JavascriptCompiler.compile("f(x)", withF("abs")).getDoubleValuesSource(bindings),
                positive);
        Query negPositive =
            new FunctionMatchQuery(
                JavascriptCompiler.compile("f(x)", withF("negate")).getDoubleValuesSource(bindings),
                positive);

        // without a query cache: abs(x) > 0 matches 5 docs, -x > 0 matches 3 docs
        IndexSearcher plain = new IndexSearcher(reader);
        plain.setQueryCache(null);
        assertEquals(5, plain.count(absPositive));
        assertEquals(3, plain.count(negPositive));

        // with a query cache that caches every query on every segment
        IndexSearcher cached = new IndexSearcher(reader);
        cached.setQueryCache(new LRUQueryCache(100, 1 << 20, _ -> true, Float.POSITIVE_INFINITY));
        cached.setQueryCachingPolicy(
            new QueryCachingPolicy() {
              @Override
              public void onUse(Query query) {}

              @Override
              public boolean shouldCache(Query query) {
                return true;
              }
            });
        assertEquals(5, cached.count(absPositive));
        assertEquals("-x > 0 through the query cache", 3, cached.count(negPositive));
      }
    }
  }
}
