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
package org.apache.lucene.facet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * DrillSideways (sequential mode) must return the same drill-down hits and drill-sideways hits as
 * the equivalent boolean queries, and as DrillSideways in concurrent mode. In the "drill-down
 * advance" strategy of DrillSidewaysScorer, the two-phase check of the second cheapest drill-down
 * dimension is skipped when the segment has no deleted documents (acceptDocs == null), so a
 * document that matches only that dimension's approximation is treated as matching it.
 */
public class TestDrillSidewaysDim1TwoPhase extends LuceneTestCase {

  /** Collects global doc ids. */
  static final class DocIdsCollector extends SimpleCollector {
    final Set<Integer> docs = new TreeSet<>();
    int docBase;

    @Override
    protected void doSetNextReader(LeafReaderContext context) {
      docBase = context.docBase;
    }

    @Override
    public void collect(int doc) {
      docs.add(docBase + doc);
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  static final class DocIdsManager implements CollectorManager<DocIdsCollector, Set<Integer>> {
    @Override
    public DocIdsCollector newCollector() {
      return new DocIdsCollector();
    }

    @Override
    public Set<Integer> reduce(Collection<DocIdsCollector> collectors) {
      Set<Integer> all = new TreeSet<>();
      for (DocIdsCollector c : collectors) {
        all.addAll(c.docs);
      }
      return all;
    }
  }

  private static Set<Integer> brute(IndexSearcher s, Query... required) throws IOException {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(MatchAllDocsQuery.INSTANCE, Occur.MUST);
    for (Query q : required) {
      b.add(q, Occur.FILTER);
    }
    return s.search(b.build(), new DocIdsManager());
  }

  private static List<DocIdsManager> twoManagers() {
    List<DocIdsManager> l = new ArrayList<>();
    l.add(new DocIdsManager());
    l.add(new DocIdsManager());
    return l;
  }

  /**
   * Docs 0 and 1 match dim a (cost 2). Docs 0..3 are in dim b's approximation (cost 4), but only
   * docs 0 and 2 really match dim b. The remaining 196 docs match neither, so the MatchAll base
   * query (cost 200) is more than 10x the cost of dim b and DrillSidewaysScorer picks
   * doDrillDownAdvanceScoring.
   */
  private void check(Query dimA, Query dimB, Directory dir) throws Exception {
    DirectoryReader r = DirectoryReader.open(dir);
    assertEquals(1, r.leaves().size());
    assertFalse(r.hasDeletions());
    IndexSearcher searcher = new IndexSearcher(r);
    searcher.setQueryCache(null);

    FacetsConfig config = new FacetsConfig();
    DrillDownQuery ddq = new DrillDownQuery(config);
    ddq.add("a", dimA);
    ddq.add("b", dimB);

    Set<Integer> expectedHits = brute(searcher, dimA, dimB);
    Set<Integer> expectedSidewaysA = brute(searcher, dimB);
    Set<Integer> expectedSidewaysB = brute(searcher, dimA);
    assertEquals(Set.of(0), expectedHits);
    assertEquals(Set.of(0, 2), expectedSidewaysA);
    assertEquals(Set.of(0, 1), expectedSidewaysB);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      DrillSideways.Result<Set<Integer>, Set<Integer>> conc =
          new DrillSideways(searcher, config, null, null, executor)
              .search(ddq, new DocIdsManager(), twoManagers());
      assertEquals(expectedHits, conc.drillDownResult());
      assertEquals(expectedSidewaysA, conc.drillSidewaysResults().get(0));
      assertEquals(expectedSidewaysB, conc.drillSidewaysResults().get(1));
    } finally {
      executor.shutdown();
    }

    DrillSideways.Result<Set<Integer>, Set<Integer>> seq =
        new DrillSideways(searcher, config, (TaxonomyReader) null)
            .search(ddq, new DocIdsManager(), twoManagers());
    String report =
        "drill-down hits: expected "
            + expectedHits
            + " got "
            + seq.drillDownResult()
            + "; sideways(a): expected "
            + expectedSidewaysA
            + " got "
            + seq.drillSidewaysResults().get(0)
            + "; sideways(b): expected "
            + expectedSidewaysB
            + " got "
            + seq.drillSidewaysResults().get(1);
    boolean ok =
        expectedHits.equals(seq.drillDownResult())
            && expectedSidewaysA.equals(seq.drillSidewaysResults().get(0))
            && expectedSidewaysB.equals(seq.drillSidewaysResults().get(1));
    r.close();
    assertTrue("sequential DrillSideways differs from the reference: " + report, ok);
  }

  /** Dim b is a PhraseQuery: docs 1 and 3 contain "y x", not the phrase "x y". */
  public void testPhraseSecondDim() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
    String[] body = {"x y", "y x", "x y", "y x"};
    for (int i = 0; i < 200; i++) {
      Document doc = new Document();
      if (i < 2) {
        doc.add(new StringField("a", "1", Field.Store.NO));
      }
      doc.add(new TextField("body", i < 4 ? body[i] : "filler", Field.Store.NO));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.close();
    check(new TermQuery(new Term("a", "1")), new PhraseQuery("body", "x", "y"), dir);
    dir.close();
  }

  /**
   * Dim b is SortedSetDocValuesField.newSlowExactQuery("color", "red") on a sparse doc-values
   * field without a skip index: docs 1 and 3 have color=blue.
   */
  public void testSlowExactDocValuesSecondDim() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
    String[] color = {"red", "blue", "red", "blue"};
    for (int i = 0; i < 200; i++) {
      Document doc = new Document();
      if (i < 2) {
        doc.add(new StringField("a", "1", Field.Store.NO));
      }
      if (i < 4) {
        doc.add(new SortedSetDocValuesField("color", new BytesRef(color[i])));
      }
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.close();
    check(
        new TermQuery(new Term("a", "1")),
        SortedSetDocValuesField.newSlowExactQuery("color", new BytesRef("red")),
        dir);
    dir.close();
  }
}
