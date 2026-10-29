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
package org.apache.lucene.queryparser.flexible.core.processors;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.processors.BoostQueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.standard.processors.PointQueryNodeProcessor;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryNodeProcessorPipeline implements java.util.List and hands every processor put on it through
 * add/add(int)/addAll/set the pipeline's QueryConfigHandler. Its listIterator() and subList() return
 * the backing list's own views, so a processor put on the pipeline through them (or through the
 * List default method replaceAll, or Collections.replaceAll, which use listIterator().set) never
 * gets the handler. Stock processors that tolerate a null handler then silently do nothing.
 */
public class TestPipelineListViewsConfigHandler extends LuceneTestCase {

  private static StandardQueryParser newParser() {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    qp.setPointsConfigMap(
        Map.of("n", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Integer.class)));
    qp.setFieldsBoost(Map.of("f", 2f));
    return qp;
  }

  private static int indexOf(QueryNodeProcessorPipeline pipe, Class<?> c) {
    for (int i = 0; i < pipe.size(); i++) {
      if (pipe.get(i).getClass() == c) {
        return i;
      }
    }
    throw new AssertionError("no " + c.getSimpleName() + " on the pipeline");
  }

  /** Replaces the stock processor of class c by a fresh instance through the given path. */
  private static Query parseAfterReplacing(
      Class<? extends QueryNodeProcessor> c,
      BiConsumer<QueryNodeProcessorPipeline, Integer> path,
      String query)
      throws Exception {
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline pipe = (QueryNodeProcessorPipeline) qp.getQueryNodeProcessor();
    path.accept(pipe, indexOf(pipe, c));
    return qp.parse(query, "f");
  }

  private static QueryNodeProcessor fresh(QueryNodeProcessorPipeline pipe, int k) {
    try {
      return pipe.get(k).getClass().getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static String cls(Query q) {
    Class<?> c = q.getClass();
    return c.isAnonymousClass() ? c.getSuperclass().getSimpleName() : c.getSimpleName();
  }

  private static Map<String, BiConsumer<QueryNodeProcessorPipeline, Integer>> paths() {
    Map<String, BiConsumer<QueryNodeProcessorPipeline, Integer>> m = new java.util.LinkedHashMap<>();
    // reference: the overriding mutators
    m.put("set(k, p)", (pipe, k) -> pipe.set(k, fresh(pipe, k)));
    m.put(
        "remove(k); add(k, p)",
        (pipe, k) -> {
          QueryNodeProcessor p = fresh(pipe, k);
          pipe.remove((int) k);
          pipe.add(k, p);
        });
    // the List views and List/Collections methods built on them
    m.put(
        "listIterator(k).next(); set(p)",
        (pipe, k) -> {
          QueryNodeProcessor p = fresh(pipe, k);
          ListIterator<QueryNodeProcessor> it = pipe.listIterator(k);
          it.next();
          it.set(p);
        });
    m.put(
        "remove(k); listIterator(k).add(p)",
        (pipe, k) -> {
          QueryNodeProcessor p = fresh(pipe, k);
          pipe.remove((int) k);
          pipe.listIterator(k).add(p);
        });
    m.put("subList(k, k + 1).set(0, p)", (pipe, k) -> pipe.subList(k, k + 1).set(0, fresh(pipe, k)));
    m.put(
        "remove(k); subList(k, k).add(p)",
        (pipe, k) -> {
          QueryNodeProcessor p = fresh(pipe, k);
          pipe.remove((int) k);
          pipe.subList(k, k).add(p);
        });
    m.put(
        "replaceAll(x -> x == old ? p : x)",
        (pipe, k) -> {
          QueryNodeProcessor old = pipe.get(k);
          QueryNodeProcessor p = fresh(pipe, k);
          pipe.replaceAll(x -> x == old ? p : x);
        });
    m.put(
        "Collections.replaceAll(pipe, old, p)",
        (pipe, k) -> Collections.replaceAll(pipe, pipe.get(k), fresh(pipe, k)));
    return m;
  }

  /** Every insertion path must give the parse result of set(k, p) / add(k, p). */
  public void testEveryInsertionPathParsesLikeSetAndAdd() throws Exception {
    List<String> wrong = new ArrayList<>();
    for (Class<? extends QueryNodeProcessor> c :
        List.of(PointQueryNodeProcessor.class, BoostQueryNodeProcessor.class)) {
      String query = c == PointQueryNodeProcessor.class ? "n:5" : "f:foo";
      Query expected = parseAfterReplacing(c, paths().get("set(k, p)"), query);
      for (Map.Entry<String, BiConsumer<QueryNodeProcessorPipeline, Integer>> e :
          paths().entrySet()) {
        Query actual = parseAfterReplacing(c, e.getValue(), query);
        if (expected.equals(actual) == false) {
          wrong.add(
              c.getSimpleName()
                  + " via "
                  + e.getKey()
                  + ": '"
                  + query
                  + "' parsed to "
                  + actual
                  + " ("
                  + cls(actual)
                  + "), expected "
                  + expected
                  + " ("
                  + cls(expected)
                  + ")");
        }
      }
    }
    assertTrue(String.join("\n", wrong), wrong.isEmpty());
  }

  /** End to end: the numeric query found through a pipeline edited with replaceAll misses the doc. */
  public void testPointQueryAfterReplaceAllFindsTheDocument() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new IntPoint("n", 5));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query viaSet = parseAfterReplacing(PointQueryNodeProcessor.class, paths().get("set(k, p)"), "n:5");
        assertEquals("query " + viaSet, 1, s.count(viaSet));
        Query viaReplaceAll =
            parseAfterReplacing(
                PointQueryNodeProcessor.class, paths().get("replaceAll(x -> x == old ? p : x)"), "n:5");
        assertEquals("query " + viaReplaceAll, 1, s.count(viaReplaceAll));
      }
    }
  }
}
