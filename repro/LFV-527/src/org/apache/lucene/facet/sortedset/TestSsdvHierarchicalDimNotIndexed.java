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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Facets#getTopChildren / #getAllChildren must return null for a dimension that was never seen
 * (Facets.java javadoc). SortedSetDocValues faceting does so for a flat dimension, but throws
 * NullPointerException when the dimension is configured as hierarchical and the reader holds no
 * document with it.
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
    config.setHierarchical("category", true); // declared, but no document has it yet
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    Document doc = new Document();
    doc.add(new SortedSetDocValuesFacetField("author", "bob"));
    w.addDocument(config.build(doc));
    w.close();
    reader = DirectoryReader.open(dir);
    state = new DefaultSortedSetDocValuesReaderState(reader, config);
    IndexSearcher searcher = new IndexSearcher(reader);
    hits = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, dir);
    super.tearDown();
  }

  public void testGetTopChildren() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
    assertNotNull(facets.getTopChildren(10, "author"));
    FacetResult result = facets.getTopChildren(10, "category"); // NullPointerException on main
    assertNull(result);
  }

  public void testGetAllChildren() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
    FacetResult result = facets.getAllChildren("category"); // NullPointerException on main
    assertNull(result);
  }

  public void testGetTopChildrenCountAll() throws Exception {
    Facets facets = new SortedSetDocValuesFacetCounts(state);
    FacetResult result = facets.getTopChildren(10, "category"); // NullPointerException on main
    assertNull(result);
  }

  public void testConcurrentGetTopChildren() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Facets facets = new ConcurrentSortedSetDocValuesFacetCounts(state, hits, executor);
      FacetResult result = facets.getTopChildren(10, "category"); // NullPointerException on main
      assertNull(result);
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  public void testFlatDimNotIndexedIsNull() throws Exception {
    // control: the same situation for a flat dimension returns null, as documented
    Facets facets = new SortedSetDocValuesFacetCounts(state, hits);
    assertNull(facets.getTopChildren(10, "publisher"));
    assertNull(facets.getAllChildren("publisher"));
  }
}
