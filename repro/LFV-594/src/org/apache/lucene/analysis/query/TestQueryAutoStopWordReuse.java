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
package org.apache.lucene.analysis.query;

import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * QueryAutoStopWordAnalyzer computes a different stop-word set per field and adds a StopFilter
 * per field in wrapComponents, but it passes its delegate's ReuseStrategy to the AnalyzerWrapper
 * constructor. Stock analyzers such as StandardAnalyzer use GLOBAL_REUSE_STRATEGY, so the
 * components built for the first field analysed are reused for every other field.
 *
 * <p>This is the upstream testNoFieldNamePollution check with a stock analyzer as the delegate
 * instead of MockAnalyzer (which uses PER_FIELD_REUSE_STRATEGY and so hides the problem).
 */
public class TestQueryAutoStopWordReuse extends BaseTokenStreamTestCase {

  private Directory dir;
  private IndexReader reader;
  private Analyzer delegate;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    delegate = new StandardAnalyzer(); // GLOBAL_REUSE_STRATEGY, like most stock analyzers
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(delegate))) {
      // "boring" is in every document of repetitiveField and in one document of variedField
      for (int i = 0; i < 20; i++) {
        Document doc = new Document();
        doc.add(new TextField("repetitiveField", "boring", Field.Store.NO));
        doc.add(new TextField("variedField", i == 0 ? "boring" : "word" + i, Field.Store.NO));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    delegate.close();
    super.tearDown();
  }

  /** The stop words of repetitiveField leak into variedField (a query term is dropped). */
  public void testStopWordsOfOneFieldAppliedToAnother() throws Exception {
    try (QueryAutoStopWordAnalyzer a =
        new QueryAutoStopWordAnalyzer(delegate, reader, Arrays.asList("repetitiveField"), 10)) {
      assertEquals(1, a.getStopWords("repetitiveField").length);
      assertEquals(0, a.getStopWords("variedField").length);

      // Check filter set up OK
      assertTokenStreamContents(a.tokenStream("repetitiveField", "boring"), new String[0]);
      // Filter should not prevent stopwords in one field being used in another
      assertTokenStreamContents(a.tokenStream("variedField", "boring"), new String[] {"boring"});
    }
  }

  /** The reverse order: the stop filter of repetitiveField is silently skipped. */
  public void testStopWordsOfFieldSkippedAfterAnotherField() throws Exception {
    try (QueryAutoStopWordAnalyzer a =
        new QueryAutoStopWordAnalyzer(delegate, reader, Arrays.asList("repetitiveField"), 10)) {
      assertTokenStreamContents(a.tokenStream("variedField", "boring"), new String[] {"boring"});
      // "boring" is a stop word of repetitiveField and must be removed there
      assertTokenStreamContents(a.tokenStream("repetitiveField", "boring"), new String[0]);
    }
  }

  /** The same defect seen through a query builder: the query for variedField is lost. */
  public void testQueryOnOtherFieldIsLost() throws Exception {
    try (QueryAutoStopWordAnalyzer a =
        new QueryAutoStopWordAnalyzer(delegate, reader, Arrays.asList("repetitiveField"), 10)) {
      QueryBuilder qb = new QueryBuilder(a);
      assertNull(qb.createBooleanQuery("repetitiveField", "boring"));
      Query q = qb.createBooleanQuery("variedField", "boring");
      assertNotNull("query on variedField was dropped by repetitiveField's stop words", q);
    }
  }

  /** End to end: a multi-field query parser drops the term on the field that has no stop words. */
  public void testMultiFieldQueryParser() throws Exception {
    try (QueryAutoStopWordAnalyzer a =
        new QueryAutoStopWordAnalyzer(delegate, reader, Arrays.asList("repetitiveField"), 10)) {
      MultiFieldQueryParser qp =
          new MultiFieldQueryParser(new String[] {"repetitiveField", "variedField"}, a);
      Query q = qp.parse("boring");
      assertEquals("variedField:boring", q.toString());
    }
  }
}
