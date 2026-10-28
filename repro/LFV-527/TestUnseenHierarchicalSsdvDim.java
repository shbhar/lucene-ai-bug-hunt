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
package org.apache.lucene.facet.verify;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.ConcurrentSortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Facets#getTopChildren and Facets#getAllChildren: "Returns null if the specified path doesn't
 * exist or if this dimension was never seen." This holds for SortedSetDocValues faceting when the
 * never-seen dimension is flat, but not when FacetsConfig declares it hierarchical.
 *
 * <p>Uses public API only (the test lives in a package with no Lucene classes).
 */
public class TestUnseenHierarchicalSsdvDim extends LuceneTestCase {

  private static final String HIER = "path"; // configured hierarchical, may have no document
  private static final String FLAT_UNSEEN = "size"; // flat, never indexed (control)

  private static FacetsConfig config() {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical(HIER, true);
    return config;
  }

  /** Index two docs that only carry the flat dim "color"; HIER is configured but never indexed. */
  private static Directory indexWithoutHierDim(FacetsConfig config) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String color : new String[] {"red", "blue"}) {
        Document doc = new Document();
        doc.add(new StringField("id", color, Field.Store.NO));
        doc.add(new SortedSetDocValuesFacetField("color", color));
        w.addDocument(config.build(doc));
      }
      w.commit();
    }
    return dir;
  }

  private interface Call {
    FacetResult run() throws Exception;
  }

  /** Assert the documented outcome (null) and report any exception as the failure cause. */
  private static void assertNullResult(String what, Call call) throws Exception {
    FacetResult r;
    try {
      r = call.run();
    } catch (NullPointerException e) {
      throw new AssertionError(
          what + ": expected null (dimension never seen, Facets javadoc), got " + e, e);
    }
    assertNull(what + ": expected null, got " + r, r);
  }

  private static FacetsCollector collect(IndexSearcher searcher, Query q) throws IOException {
    return searcher.search(q, new FacetsCollectorManager());
  }

  // ---- controls: the same calls that the contract covers, which work today ----

  /** Control: a never-seen FLAT dim returns null from both calls. */
  public void testControlFlatUnseenDimReturnsNull() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = indexWithoutHierDim(config);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      FacetsCollector fc = collect(searcher, new MatchAllDocsQuery());
      Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
      assertNullResult("flat getTopChildren", () -> facets.getTopChildren(10, FLAT_UNSEEN));
      assertNullResult("flat getAllChildren", () -> facets.getAllChildren(FLAT_UNSEEN));
      // and the configured-hierarchical dim with a non-empty path takes the lookupTerm path: null
      assertNullResult("hier getTopChildren(path)", () -> facets.getTopChildren(10, HIER, "a"));
      assertNullResult("hier getAllChildren(path)", () -> facets.getAllChildren(HIER, "a"));
    }
  }

  /** Control: once a document has the hierarchical dim, both calls work. */
  public void testControlHierDimPresentWorks() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new SortedSetDocValuesFacetField("color", "red"));
        doc.add(new SortedSetDocValuesFacetField(HIER, "a", "b"));
        w.addDocument(config.build(doc));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);
        Facets facets =
            new SortedSetDocValuesFacetCounts(state, collect(searcher, new MatchAllDocsQuery()));
        FacetResult top = facets.getTopChildren(10, HIER);
        assertNotNull(top);
        assertEquals(1, top.childCount);
        assertEquals("a", top.labelValues[0].label);
        assertNotNull(facets.getAllChildren(HIER));
      }
    }
  }

  // ---- the claim ----

  /** SortedSetDocValuesFacetCounts over hits, getTopChildren of the unseen hierarchical dim. */
  public void testTopChildrenOverHits() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = indexWithoutHierDim(config);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      Facets facets =
          new SortedSetDocValuesFacetCounts(state, collect(searcher, new MatchAllDocsQuery()));
      assertNullResult("getTopChildren(10, HIER)", () -> facets.getTopChildren(10, HIER));
    }
  }

  /** SortedSetDocValuesFacetCounts over hits, getAllChildren of the unseen hierarchical dim. */
  public void testAllChildrenOverHits() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = indexWithoutHierDim(config);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      Facets facets =
          new SortedSetDocValuesFacetCounts(state, collect(searcher, new MatchAllDocsQuery()));
      assertNullResult("getAllChildren(HIER)", () -> facets.getAllChildren(HIER));
    }
  }

  /** Count-all constructor (no FacetsCollector). */
  public void testCountAllConstructor() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = indexWithoutHierDim(config);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      Facets facets = new SortedSetDocValuesFacetCounts(state);
      assertNullResult("count-all getTopChildren(10, HIER)", () -> facets.getTopChildren(10, HIER));
    }
  }

  /**
   * Zero-hit query: getTopChildren short-circuits on hasCounts() and returns null, but
   * getAllChildren dereferences the dim tree before that check.
   */
  public void testAllChildrenZeroHits() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = indexWithoutHierDim(config);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      FacetsCollector fc = collect(searcher, new TermQuery(new Term("id", "no-such-id")));
      Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
      assertNullResult(
          "zero-hit getTopChildren(10, HIER)", () -> facets.getTopChildren(10, HIER));
      assertNullResult("zero-hit getAllChildren(HIER)", () -> facets.getAllChildren(HIER));
    }
  }

  /** ConcurrentSortedSetDocValuesFacetCounts shares the code path. */
  public void testConcurrentCounts() throws Exception {
    FacetsConfig config = config();
    ExecutorService exec = Executors.newFixedThreadPool(2);
    try (Directory dir = indexWithoutHierDim(config);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state =
          new DefaultSortedSetDocValuesReaderState(reader, config);
      Facets facets =
          new ConcurrentSortedSetDocValuesFacetCounts(
              state, collect(searcher, new MatchAllDocsQuery()), exec);
      assertNullResult("concurrent getTopChildren(10, HIER)", () -> facets.getTopChildren(10, HIER));
    } finally {
      exec.shutdown();
      assertTrue(exec.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  /**
   * A natural trigger: the hierarchical dim was indexed, then every document holding it was
   * deleted and merged away. The reader no longer has the dim, and the same call that worked
   * before now throws.
   */
  public void testDimDisappearsAfterDeleteAndMerge() throws Exception {
    FacetsConfig config = config();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document a = new Document();
        a.add(new StringField("id", "a", Field.Store.NO));
        a.add(new SortedSetDocValuesFacetField(HIER, "x", "y"));
        a.add(new SortedSetDocValuesFacetField("color", "red"));
        w.addDocument(config.build(a));
        Document b = new Document();
        b.add(new StringField("id", "b", Field.Store.NO));
        b.add(new SortedSetDocValuesFacetField("color", "blue"));
        w.addDocument(config.build(b));
        w.commit();
        try (DirectoryReader before = DirectoryReader.open(dir)) {
          SortedSetDocValuesReaderState s0 =
              new DefaultSortedSetDocValuesReaderState(before, config);
          assertNotNull(
              "before delete", new SortedSetDocValuesFacetCounts(s0).getTopChildren(10, HIER));
        }
        w.deleteDocuments(new Term("id", "a"));
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
        assertEquals(1, reader.maxDoc()); // the deleted doc is merged away
        IndexSearcher searcher = new IndexSearcher(reader);
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);
        Facets facets =
            new SortedSetDocValuesFacetCounts(state, collect(searcher, new MatchAllDocsQuery()));
        assertNullResult(
            "after delete+merge getTopChildren(10, HIER)", () -> facets.getTopChildren(10, HIER));
      }
    }
  }
}
