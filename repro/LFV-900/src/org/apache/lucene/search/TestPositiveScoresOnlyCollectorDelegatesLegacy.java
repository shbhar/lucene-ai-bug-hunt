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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Same two checks as TestPositiveScoresOnlyCollectorDelegates, written against the 8.x/9.x API
 * (IndexSearcher#search(Query, Collector), TopFieldCollector#create; 9.1+ test-framework packages).
 */
public class TestPositiveScoresOnlyCollectorDelegatesLegacy extends LuceneTestCase {

  private static final Query ALL_ZERO = new BoostQuery(new TermQuery(new Term("f", "x")), 0f);

  private static final Query MIXED =
      new BooleanQuery.Builder()
          .add(new BoostQuery(new TermQuery(new Term("g", "y")), 2f), Occur.SHOULD)
          .add(new BoostQuery(new TermQuery(new Term("f", "x")), 0f), Occur.SHOULD)
          .build();

  private static final Query ALL_POSITIVE = new TermQuery(new Term("g", "y"));

  private Directory index(int numSegments) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE);
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 12; i++) {
      Document doc = new Document();
      doc.add(new StringField("f", "x", Field.Store.NO));
      if (i % 2 == 0) {
        doc.add(new StringField("g", "y", Field.Store.NO));
      }
      doc.add(new NumericDocValuesField("sort", i));
      w.addDocument(doc);
      if (numSegments > 1 && i % (12 / numSegments) == 12 / numSegments - 1) {
        w.commit();
      }
    }
    w.commit();
    w.close();
    return dir;
  }

  private static IndexSearcher searcher(DirectoryReader reader) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    return searcher;
  }

  private static int positiveCount(IndexSearcher searcher, Query query) throws IOException {
    int n = 0;
    for (ScoreDoc sd : searcher.search(query, 100).scoreDocs) {
      if (sd.score > 0) {
        n++;
      }
    }
    return n;
  }

  public void testWrappedTotalHitCountCollector() throws IOException {
    List<String> wrong = new ArrayList<>();
    for (int numSegments : new int[] {1, 3}) {
      Directory dir = index(numSegments);
      DirectoryReader reader = DirectoryReader.open(dir);
      IndexSearcher searcher = searcher(reader);
      for (Query query : new Query[] {ALL_ZERO, MIXED}) {
        int expected = positiveCount(searcher, query);
        TotalHitCountCollector counter = new TotalHitCountCollector();
        searcher.search(query, new PositiveScoresOnlyCollector(counter));
        int actual = counter.getTotalHits();
        if (actual != expected) {
          wrong.add(
              "segments=" + reader.leaves().size() + " query=" + query + " expected=" + expected
                  + " actual=" + actual);
        }
      }
      reader.close();
      dir.close();
    }
    assertTrue(
        "PositiveScoresOnlyCollector(TotalHitCountCollector) counts: " + wrong, wrong.isEmpty());
  }

  public void testWrappedSortByFieldCollector() throws IOException {
    List<String> wrong = new ArrayList<>();
    for (int numSegments : new int[] {1, 3}) {
      Directory dir = index(numSegments);
      DirectoryReader reader = DirectoryReader.open(dir);
      IndexSearcher searcher = searcher(reader);
      for (Query query : new Query[] {ALL_ZERO, MIXED, ALL_POSITIVE}) {
        int expected = positiveCount(searcher, query);
        TopFieldCollector topField =
            TopFieldCollector.create(
                new Sort(new SortField("sort", SortField.Type.LONG)), 100, 1000);
        searcher.search(query, new PositiveScoresOnlyCollector(topField));
        int actual = topField.topDocs().scoreDocs.length;
        if (actual != expected) {
          wrong.add(
              "segments=" + reader.leaves().size() + " query=" + query + " sorted by field:"
                  + " expected=" + expected + " hits, actual=" + actual + " (delegate scoreMode="
                  + topField.scoreMode() + ")");
        }
      }
      reader.close();
      dir.close();
    }
    assertTrue("PositiveScoresOnlyCollector(TopFieldCollector) hits: " + wrong, wrong.isEmpty());
  }
}
