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
package org.apache.lucene.queryparser.flexible.standard;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Locale;
import java.util.function.Consumer;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.processors.AnalyzerQueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.standard.processors.BoostQueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.standard.processors.PointQueryNodeProcessor;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a processor put on StandardQueryParser's pipeline through the pipeline's
 * List views (listIterator / subList) or the List.replaceAll default method should end up with the
 * pipeline's QueryConfigHandler, exactly like one put there with the pipeline's own set(int, p).
 * Public API only.
 */
public class TestPipelineViewEditsV1 extends LuceneTestCase {

  private static StandardQueryParser newParser() {
    StandardQueryParser qp = new StandardQueryParser(new MockAnalyzer(random()));
    qp.setPointsConfigMap(
        Collections.singletonMap(
            "n", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Integer.class)));
    qp.setFieldsBoost(Collections.singletonMap("f", 2f));
    return qp;
  }

  private static QueryNodeProcessorPipeline pipeline(StandardQueryParser qp) {
    return (QueryNodeProcessorPipeline) qp.getQueryNodeProcessor();
  }

  private static int indexOf(QueryNodeProcessorPipeline p, Class<?> c) {
    for (int i = 0; i < p.size(); i++) {
      if (p.get(i).getClass() == c) {
        return i;
      }
    }
    throw new AssertionError("no " + c.getSimpleName() + " on the pipeline");
  }

  /** Replaces the processor of class c through the given edit, then parses q. */
  private static Query parseAfter(
      Class<?> c, String q, String defaultField, Consumer<QueryNodeProcessorPipeline> edit)
      throws Exception {
    StandardQueryParser qp = newParser();
    edit.accept(pipeline(qp));
    return qp.parse(q, defaultField);
  }

  // ---- control: the pipeline's own set(int, p) (expected to pass) ----

  public void testControlSetIntSetsHandler() throws Exception {
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline p = pipeline(qp);
    int k = indexOf(p, PointQueryNodeProcessor.class);
    QueryNodeProcessor fresh = new PointQueryNodeProcessor();
    p.set(k, fresh);
    assertSame(qp.getQueryConfigHandler(), fresh.getQueryConfigHandler());
    Query expected = newParser().parse("n:5", "x"); // unedited parser
    assertEquals(expected, qp.parse("n:5", "x"));
  }

  // ---- the view paths ----

  public void testListIteratorSetPoint() throws Exception {
    Query oracle =
        parseAfter(
            PointQueryNodeProcessor.class,
            "n:5",
            "x",
            p -> p.set(indexOf(p, PointQueryNodeProcessor.class), new PointQueryNodeProcessor()));
    Query actual =
        parseAfter(
            PointQueryNodeProcessor.class,
            "n:5",
            "x",
            p -> {
              ListIterator<QueryNodeProcessor> it =
                  p.listIterator(indexOf(p, PointQueryNodeProcessor.class));
              it.next();
              it.set(new PointQueryNodeProcessor());
            });
    assertEquals("listIterator().set vs set(int,p)", oracle, actual);
  }

  public void testListIteratorAddSetsHandler() throws Exception {
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline p = pipeline(qp);
    QueryNodeProcessor inserted = new PointQueryNodeProcessor();
    // classic "insert after X" idiom
    ListIterator<QueryNodeProcessor> it = p.listIterator();
    while (it.hasNext()) {
      if (it.next() instanceof PointQueryNodeProcessor) {
        it.remove();
        it.add(inserted);
        break;
      }
    }
    assertTrue(p.contains(inserted));
    assertSame(
        "processor inserted through listIterator().add has the pipeline's handler",
        p.getQueryConfigHandler(),
        inserted.getQueryConfigHandler());
  }

  public void testSubListSetBoost() throws Exception {
    Query oracle =
        parseAfter(
            BoostQueryNodeProcessor.class,
            "f:foo",
            "x",
            p -> p.set(indexOf(p, BoostQueryNodeProcessor.class), new BoostQueryNodeProcessor()));
    Query actual =
        parseAfter(
            BoostQueryNodeProcessor.class,
            "f:foo",
            "x",
            p -> {
              int k = indexOf(p, BoostQueryNodeProcessor.class);
              p.subList(k, k + 1).set(0, new BoostQueryNodeProcessor());
            });
    assertEquals("subList().set vs set(int,p)", oracle, actual);
  }

  public void testReplaceAllPoint() throws Exception {
    Query oracle =
        parseAfter(
            PointQueryNodeProcessor.class,
            "n:5",
            "x",
            p -> p.set(indexOf(p, PointQueryNodeProcessor.class), new PointQueryNodeProcessor()));
    Query actual =
        parseAfter(
            PointQueryNodeProcessor.class,
            "n:5",
            "x",
            p ->
                p.replaceAll(
                    proc ->
                        proc instanceof PointQueryNodeProcessor
                            ? new PointQueryNodeProcessor()
                            : proc));
    assertEquals("List.replaceAll vs set(int,p)", oracle, actual);
  }

  /** Workaround control: re-setting the handler on the pipeline after the view edit fixes it. */
  public void testControlReplaceAllThenResetHandler() throws Exception {
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline p = pipeline(qp);
    p.replaceAll(
        proc -> proc instanceof PointQueryNodeProcessor ? new PointQueryNodeProcessor() : proc);
    p.setQueryConfigHandler(qp.getQueryConfigHandler());
    assertEquals(newParser().parse("n:5", "x"), qp.parse("n:5", "x"));
  }

  /** End to end: the query from a listIterator-edited parser must still find the document. */
  public void testSearchAfterListIteratorSet() throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
        Document d = new Document();
        d.add(new IntPoint("n", 5));
        w.addDocument(d);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query unedited = newParser().parse("n:5", "x");
        assertEquals("unedited parser finds the doc", 1, s.count(unedited));
        Query edited =
            parseAfter(
                PointQueryNodeProcessor.class,
                "n:5",
                "x",
                p -> {
                  ListIterator<QueryNodeProcessor> it =
                      p.listIterator(indexOf(p, PointQueryNodeProcessor.class));
                  it.next();
                  it.set(new PointQueryNodeProcessor());
                });
        assertEquals("query " + edited + " after listIterator().set", 1, s.count(edited));
      }
    }
  }

  /** Collections.replaceAll on the (non-RandomAccess, 21-element) pipeline uses listIterator. */
  public void testCollectionsReplaceAllBoost() throws Exception {
    Query oracle = newParser().parse("f:foo", "x");
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline p = pipeline(qp);
    QueryNodeProcessor old = p.get(indexOf(p, BoostQueryNodeProcessor.class));
    assertTrue(Collections.replaceAll(p, old, new BoostQueryNodeProcessor()));
    assertEquals("Collections.replaceAll vs unedited", oracle, qp.parse("f:foo", "x"));
  }

  /** Loud variant: a processor that dereferences the handler throws NullPointerException. */
  public void testListIteratorSetAnalyzerProcessor() throws Exception {
    Query oracle = newParser().parse("foo", "x");
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline p = pipeline(qp);
    ListIterator<QueryNodeProcessor> it = p.listIterator(indexOf(p, AnalyzerQueryNodeProcessor.class));
    it.next();
    it.set(new AnalyzerQueryNodeProcessor());
    assertEquals("listIterator().set(AnalyzerQueryNodeProcessor)", oracle, qp.parse("foo", "x"));
  }
}
