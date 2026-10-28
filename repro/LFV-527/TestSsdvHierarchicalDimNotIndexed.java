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
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Facets#getTopChildren and #getAllChildren return null "if this dimension was never seen". A
 * dimension that is configured hierarchical but that no document in the reader has must get the
 * same null as a never-seen flat dimension, not a NullPointerException.
 */
public class TestSsdvHierarchicalDimNotIndexed extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private SortedSetDocValuesReaderState state;
  private FacetsCollector hits;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("category", true); // configured, but no document has it yet
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("author", "bob"));
    w.addDocument(config.build(doc));
    w.close();
    reader = DirectoryReader.open(dir);
    state = new DefaultSortedSetDocValuesReaderState(reader, config);
    hits = new IndexSearcher(reader).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, dir);
    super.tearDown();
  }

  public void testGetTopChildren() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
    assertNotNull(facets.getTopChildren(10, "author"));
    assertNull(facets.getTopChildren(10, "category")); // NullPointerException
  }

  public void testGetAllChildren() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
    assertNull(facets.getAllChildren("category")); // NullPointerException
  }

  public void testGetTopChildrenCountAll() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state);
    assertNull(facets.getTopChildren(10, "category")); // NullPointerException
  }

  public void testConcurrentGetTopChildren() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Facets facets = new ConcurrentSortedSetDocValuesFacetCounts(state, hits, executor);
      assertNull(facets.getTopChildren(10, "category")); // NullPointerException
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  /** Controls: the same reader state is "never seen" everywhere else. */
  public void testControls() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
    // a flat dimension that no document has
    assertNull(facets.getTopChildren(10, "publisher"));
    assertNull(facets.getAllChildren("publisher"));
    // the same hierarchical dimension, asked for a path below its root
    assertNull(facets.getTopChildren(10, "category", "books"));
    assertNull(facets.getAllChildren("category", "books"));
    // the documented "doesn't exist" value
    assertEquals(-1, facets.getSpecificValue("category").intValue());
  }
}
