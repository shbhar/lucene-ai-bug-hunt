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
package org.apache.lucene.facet.sortedset;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Version-portable check (getTopChildren only, public API only; compiles against every release
 * that has hierarchical SortedSetDocValues facets, 9.1.0 and later).
 *
 * <p>Facets#getTopChildren must return null for a dimension that was never seen. The dimension
 * "category" is configured hierarchical but no document has it. The test* methods check the bug;
 * the testControl* methods check behaviour that must hold in every version.
 */
public class TestSsdvHierarchicalUnseenDimHistory extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private FacetsConfig config;
  private SortedSetDocValuesReaderState state;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    config = new FacetsConfig();
    config.setHierarchical("category", true); // declared, but no document has it
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("author", "bob"));
    w.addDocument(config.build(doc));
    w.close();
    reader = DirectoryReader.open(dir);
    state = new DefaultSortedSetDocValuesReaderState(reader, config);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, dir);
    super.tearDown();
  }

  private FacetsCollector hits(Query q) throws Exception {
    return searcher.search(q, new FacetsCollectorManager());
  }

  public void testTopChildrenWithHits() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits(new MatchAllDocsQuery()));
    assertNull(facets.getTopChildren(10, "category"));
  }

  public void testTopChildrenCountAll() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state);
    assertNull(facets.getTopChildren(10, "category"));
  }

  public void testTopChildrenZeroHits() throws Exception {
    Facets facets =
        new SortedSetDocValuesFacetCounts(state, hits(new TermQuery(new Term("nope", "nope"))));
    assertNull(facets.getTopChildren(10, "category"));
  }

  public void testConcurrentTopChildren() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Facets facets =
          new ConcurrentSortedSetDocValuesFacetCounts(
              state, hits(new MatchAllDocsQuery()), executor);
      assertNull(facets.getTopChildren(10, "category"));
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  public void testControlFlatDimNotIndexed() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits(new MatchAllDocsQuery()));
    assertNotNull(facets.getTopChildren(10, "author"));
    assertNull(facets.getTopChildren(10, "publisher"));
  }

  public void testControlHierarchicalPathNotIndexed() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits(new MatchAllDocsQuery()));
    assertNull(facets.getTopChildren(10, "category", "a"));
  }

  public void testControlHierarchicalDimIndexed() throws Exception {
    // Hierarchical SSDV faceting itself works in this version when the dim has a document.
    try (Directory dir2 = newDirectory()) {
      IndexWriter w = new IndexWriter(dir2, newIndexWriterConfig());
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("category", "a", "b"));
      w.addDocument(config.build(doc));
      w.close();
      try (DirectoryReader r2 = DirectoryReader.open(dir2)) {
        SortedSetDocValuesReaderState s2 = new DefaultSortedSetDocValuesReaderState(r2, config);
        FacetsCollector fc = new IndexSearcher(r2).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        FacetResult result = new SortedSetDocValuesFacetCounts(s2, fc).getTopChildren(10, "category");
        assertNotNull(result);
        assertEquals(1, result.childCount);
        assertEquals("a", result.labelValues[0].label);
        assertEquals(1, result.labelValues[0].value.intValue());
      }
    }
  }
}
