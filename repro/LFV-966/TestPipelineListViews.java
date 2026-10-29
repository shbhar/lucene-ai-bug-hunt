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

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler;
import org.apache.lucene.queryparser.flexible.standard.processors.BoostQueryNodeProcessor;
import org.apache.lucene.queryparser.flexible.standard.processors.PointQueryNodeProcessor;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryNodeProcessorPipeline gives every processor put on it through add or set its
 * QueryConfigHandler, but its listIterator() and subList() views (and List.replaceAll, which uses
 * listIterator) do not.
 */
public class TestPipelineListViews extends LuceneTestCase {

  /** A parser that knows field n holds int points and gives field f a boost of 2. */
  private static StandardQueryParser newParser() {
    StandardQueryParser qp = new StandardQueryParser(new StandardAnalyzer());
    NumberFormat format = NumberFormat.getIntegerInstance(Locale.ROOT);
    qp.setPointsConfigMap(Map.of("n", new PointsConfig(format, Integer.class)));
    qp.setFieldsBoost(Map.of("f", 2f));
    return qp;
  }

  private static QueryNodeProcessorPipeline pipeline(StandardQueryParser qp) {
    return (QueryNodeProcessorPipeline) qp.getQueryNodeProcessor();
  }

  /** Hits of q on an index with one document, IntPoint n = 5. */
  private static int count(Query q) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new IntPoint("n", 5));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return newSearcher(r).count(q);
      }
    }
  }

  /** Control, passes: replace the point processor with set(k, p); n:5 still finds the doc. */
  public void testSetKeepsPointQuery() throws Exception {
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline pipe = pipeline(qp);
    for (int k = 0; k < pipe.size(); k++) {
      if (pipe.get(k) instanceof PointQueryNodeProcessor) {
        pipe.set(k, new PointQueryNodeProcessor());
      }
    }
    Query q = qp.parse("n:5", "f");
    assertEquals(q.toString(), 1, count(q));
  }

  /** The same replacement through listIterator().set: n:5 becomes a TermQuery, 0 hits. */
  public void testListIteratorSetKeepsPointQuery() throws Exception {
    StandardQueryParser qp = newParser();
    ListIterator<QueryNodeProcessor> it = pipeline(qp).listIterator();
    while (it.hasNext()) {
      if (it.next() instanceof PointQueryNodeProcessor) {
        it.set(new PointQueryNodeProcessor());
      }
    }
    Query q = qp.parse("n:5", "f");
    assertEquals(q.toString(), 1, count(q));
  }

  /** Replace the boost processor with List.replaceAll: the field boost of f is lost. */
  public void testReplaceAllKeepsFieldBoost() throws Exception {
    StandardQueryParser qp = newParser();
    pipeline(qp)
        .replaceAll(p -> p instanceof BoostQueryNodeProcessor ? new BoostQueryNodeProcessor() : p);
    Query expected = new BoostQuery(new TermQuery(new Term("f", "foo")), 2f);
    assertEquals(expected, qp.parse("f:foo", "f"));
  }

  /** Replace the boost processor through subList(k, k + 1).set: the field boost is lost. */
  public void testSubListSetKeepsFieldBoost() throws Exception {
    StandardQueryParser qp = newParser();
    QueryNodeProcessorPipeline pipe = pipeline(qp);
    for (int k = 0; k < pipe.size(); k++) {
      if (pipe.get(k) instanceof BoostQueryNodeProcessor) {
        pipe.subList(k, k + 1).set(0, new BoostQueryNodeProcessor());
      }
    }
    Query expected = new BoostQuery(new TermQuery(new Term("f", "foo")), 2f);
    assertEquals(expected, qp.parse("f:foo", "f"));
  }

  /** A pipeline you build yourself: add(p) hands p the handler, listIterator().add(p) does not. */
  public void testListIteratorAddSetsHandler() {
    StandardQueryConfigHandler config = new StandardQueryConfigHandler();
    QueryNodeProcessorPipeline pipe = new QueryNodeProcessorPipeline(config);
    QueryNodeProcessor viaAdd = new BoostQueryNodeProcessor();
    pipe.add(viaAdd);
    QueryNodeProcessor viaListIterator = new BoostQueryNodeProcessor();
    ListIterator<QueryNodeProcessor> it = pipe.listIterator();
    it.next();
    it.add(viaListIterator); // insert after viaAdd
    assertSame(config, viaAdd.getQueryConfigHandler());
    assertSame(config, viaListIterator.getQueryConfigHandler());
  }
}
