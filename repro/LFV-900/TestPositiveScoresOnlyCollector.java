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
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the PositiveScoresOnlyCollector contract (class javadoc: "makes sure only
 * documents with scores &gt; 0 are collected"; Collector overview: "wraps any other Collector and
 * prevents collection of hits whose score is &lt;= 0.0").
 *
 * <p>Oracle: a COMPLETE-mode collector that counts the matches whose score is &gt; 0. The wrapped
 * delegate must see exactly that many hits, whatever its own ScoreMode is. Only public API is used.
 */
public class TestPositiveScoresOnlyCollector extends LuceneTestCase {

  private static final int NUM_DOCS = 20;

  private static boolean isRare(int i) {
    return i % 4 == 0; // 5 of 20 documents
  }

  /** Every match of this query scores exactly 0 (boost 0 is legal: BoostQuery requires >= 0). */
  private static Query zeroScoreQuery() {
    return new BoostQuery(new TermQuery(new Term("body", "common")), 0f);
  }

  /** Every match of this plain term query scores > 0 (BM25). */
  private static Query positiveQuery() {
    return new TermQuery(new Term("tag", "rare"));
  }

  /** 20 matches, of which the 5 "rare" documents score > 0 and the other 15 score 0. */
  private static Query mixedQuery() {
    return new BooleanQuery.Builder()
        .add(positiveQuery(), BooleanClause.Occur.SHOULD)
        .add(zeroScoreQuery(), BooleanClause.Occur.SHOULD)
        .build();
  }

  private interface DelegateFactory<C extends Collector> {
    C create() throws IOException;
  }

  /** Wraps each new delegate in a PositiveScoresOnlyCollector; reduce returns the delegates. */
  private static final class WrappingManager<C extends Collector>
      implements CollectorManager<PositiveScoresOnlyCollector, List<C>> {
    private final DelegateFactory<C> factory;
    private final List<C> delegates = new ArrayList<>();

    WrappingManager(DelegateFactory<C> factory) {
      this.factory = factory;
    }

    @Override
    public PositiveScoresOnlyCollector newCollector() throws IOException {
      C c = factory.create();
      delegates.add(c);
      return new PositiveScoresOnlyCollector(c);
    }

    @Override
    public List<C> reduce(Collection<PositiveScoresOnlyCollector> collectors) {
      return delegates;
    }
  }

  /** Oracle collector: counts matches and matches with score > 0, always with COMPLETE scores. */
  private static final class OracleCollector extends SimpleCollector {
    int matches;
    int positive;
    private Scorable scorer;

    @Override
    public void setScorer(Scorable scorer) {
      this.scorer = scorer;
    }

    @Override
    public void collect(int doc) throws IOException {
      matches++;
      if (scorer.score() > 0) {
        positive++;
      }
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE;
    }
  }

  private static final class Index implements AutoCloseable {
    final Directory dir;
    final DirectoryReader reader;
    final IndexSearcher searcher;
    final String desc;

    Index(Directory dir, int segments, boolean withDeletion) throws IOException {
      this.dir = dir;
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        int perSegment = (NUM_DOCS + segments - 1) / segments;
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          doc.add(new StringField("body", "common", Field.Store.NO));
          if (isRare(i)) {
            doc.add(new StringField("tag", "rare", Field.Store.NO));
          }
          doc.add(new NumericDocValuesField("rank", NUM_DOCS - i));
          w.addDocument(doc);
          if ((i + 1) % perSegment == 0 && i + 1 < NUM_DOCS) {
            w.commit();
          }
        }
        if (withDeletion) {
          // A document that matches none of the test queries, flushed into the last segment
          // together with the last regular documents; deleting it only makes hasDeletions() true
          // in that segment, so Weight#count can no longer shortcut there.
          Document extra = new Document();
          extra.add(new StringField("id", "extra", Field.Store.NO));
          extra.add(new NumericDocValuesField("rank", 0));
          w.addDocument(extra);
          w.deleteDocuments(new Term("id", "extra"));
        }
        w.commit();
      }
      reader = DirectoryReader.open(dir);
      searcher = new IndexSearcher(reader); // default similarity (BM25), no executor
      searcher.setQueryCache(null);
      if (reader.leaves().size() != segments || reader.hasDeletions() != withDeletion) {
        throw new AssertionError(
            "index layout not as designed: segments="
                + reader.leaves().size()
                + " hasDeletions="
                + reader.hasDeletions());
      }
      desc = "segments=" + reader.leaves().size() + " deletions=" + reader.hasDeletions();
    }

    @Override
    public void close() throws IOException {
      reader.close();
      dir.close();
    }
  }

  private static OracleCollector oracle(IndexSearcher searcher, Query q) throws IOException {
    OracleCollector oc = new OracleCollector();
    searcher.search(
        q,
        new CollectorManager<OracleCollector, OracleCollector>() {
          @Override
          public OracleCollector newCollector() {
            return oc;
          }

          @Override
          public OracleCollector reduce(Collection<OracleCollector> collectors) {
            return oc;
          }
        });
    return oc;
  }

  private static final String[] QUERY_NAMES = {"zero-score", "positive", "mixed"};

  private static Query query(int i) {
    switch (i) {
      case 0:
        return zeroScoreQuery();
      case 1:
        return positiveQuery();
      default:
        return mixedQuery();
    }
  }

  private static final int[] EXPECTED_MATCHES = {20, 5, 20};
  private static final int[] EXPECTED_POSITIVE = {0, 5, 5};

  /** Runs one wrapped search through either the CollectorManager or the (old) Collector API. */
  @SuppressWarnings("deprecation")
  private static <C extends Collector> List<C> wrappedSearch(
      IndexSearcher searcher, Query q, DelegateFactory<C> factory, boolean viaManager)
      throws IOException {
    WrappingManager<C> manager = new WrappingManager<>(factory);
    if (viaManager) {
      return searcher.search(q, manager);
    } else {
      PositiveScoresOnlyCollector c = manager.newCollector();
      searcher.search(q, c);
      return manager.delegates;
    }
  }

  private interface Measure {
    int hitsSeenByDelegate(IndexSearcher searcher, Query q, boolean viaManager) throws IOException;
  }

  private void runMatrix(String delegateName, Measure measure) throws IOException {
    List<String> wrong = new ArrayList<>();
    List<String> right = new ArrayList<>();
    for (int segments : new int[] {1, 3}) {
      for (boolean withDeletion : new boolean[] {false, true}) {
        try (Index idx = new Index(newDirectory(), segments, withDeletion)) {
          for (int qi = 0; qi < 3; qi++) {
            OracleCollector oc = oracle(idx.searcher, query(qi));
            // Self-check of the oracle (the test is meaningless if the data is not as designed).
            assertEquals(
                "oracle matches " + QUERY_NAMES[qi] + " " + idx.desc,
                EXPECTED_MATCHES[qi],
                oc.matches);
            assertEquals(
                "oracle positives " + QUERY_NAMES[qi] + " " + idx.desc,
                EXPECTED_POSITIVE[qi],
                oc.positive);
            for (boolean viaManager : new boolean[] {true, false}) {
              int actual = measure.hitsSeenByDelegate(idx.searcher, query(qi), viaManager);
              String line =
                  idx.desc
                      + " path="
                      + (viaManager ? "search(Query,CollectorManager)" : "search(Query,Collector)")
                      + " query="
                      + QUERY_NAMES[qi]
                      + " matches="
                      + oc.matches
                      + " expected(score>0)="
                      + oc.positive
                      + " actual="
                      + actual;
              if (actual != oc.positive) {
                wrong.add(line);
              } else {
                right.add(line);
              }
            }
          }
        }
      }
    }
    System.out.println("== PositiveScoresOnlyCollector(" + delegateName + ")");
    for (String s : wrong) {
      System.out.println("WRONG  " + s);
    }
    System.out.println("ok     " + right.size() + " of " + (wrong.size() + right.size()));
    if (wrong.isEmpty() == false) {
      fail(
          "PositiveScoresOnlyCollector("
              + delegateName
              + "): "
              + wrong.size()
              + " of "
              + (wrong.size() + right.size())
              + " searches gave the delegate a hit set different from the matches with score > 0: "
              + wrong);
    }
  }

  private void checkFieldSort(int totalHitsThreshold) throws IOException {
    final Sort sort = new Sort(new SortField("rank", SortField.Type.LONG));
    ScoreMode mode =
        new TopFieldCollectorManager(sort, NUM_DOCS + 10, null, totalHitsThreshold)
            .newCollector()
            .scoreMode();
    runMatrix(
        "TopFieldCollector sort=rank threshold=" + totalHitsThreshold + " scoreMode=" + mode,
        (searcher, q, viaManager) -> {
          TopFieldCollectorManager m =
              new TopFieldCollectorManager(sort, NUM_DOCS + 10, null, totalHitsThreshold);
          List<TopFieldCollector> ds = wrappedSearch(searcher, q, m::newCollector, viaManager);
          return m.reduce(ds).scoreDocs.length;
        });
  }

  /** Sort-by-field delegate that counts all hits (ScoreMode COMPLETE_NO_SCORES). */
  public void testWrappedFieldSortCollectorExhaustive() throws IOException {
    checkFieldSort(Integer.MAX_VALUE);
  }

  /**
   * Sort-by-field delegate with threshold 1000 (ScoreMode TOP_DOCS), the configuration that
   * IndexSearcher#search(Query, int, Sort) uses.
   */
  public void testWrappedFieldSortCollectorTopDocs() throws IOException {
    checkFieldSort(1000);
  }

  /** Hit-count delegate (ScoreMode COMPLETE_NO_SCORES, uses Weight#count via setWeight). */
  public void testWrappedTotalHitCountCollector() throws IOException {
    runMatrix(
        "TotalHitCountCollector scoreMode=" + new TotalHitCountCollector().scoreMode(),
        (searcher, q, viaManager) -> {
          List<TotalHitCountCollector> ds =
              wrappedSearch(searcher, q, TotalHitCountCollector::new, viaManager);
          int total = 0;
          for (TotalHitCountCollector c : ds) {
            total += c.getTotalHits();
          }
          return total;
        });
  }

  /** Control: a scoring delegate (ScoreMode COMPLETE: threshold MAX_VALUE) is filtered correctly. */
  public void testControlWrappedTopScoreDocCollector() throws IOException {
    runMatrix(
        "TopScoreDocCollector (control) scoreMode="
            + new TopScoreDocCollectorManager(NUM_DOCS + 10, null, Integer.MAX_VALUE)
                .newCollector()
                .scoreMode(),
        (searcher, q, viaManager) -> {
          TopScoreDocCollectorManager m =
              new TopScoreDocCollectorManager(NUM_DOCS + 10, null, Integer.MAX_VALUE);
          List<TopScoreDocCollector> ds = wrappedSearch(searcher, q, m::newCollector, viaManager);
          return m.reduce(ds).scoreDocs.length;
        });
  }
}
