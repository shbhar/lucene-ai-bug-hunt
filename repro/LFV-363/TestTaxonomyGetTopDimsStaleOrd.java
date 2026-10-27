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
package org.apache.lucene.facet.taxonomy;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Facets#getTopDims must return "the same as calling getAllDims and then only using the first
 * topNDims". For taxonomy facets, when a multi-valued dimension replaces the bottom entry of the
 * internal priority queue, only the entry's name and value are updated, not its dimension ordinal,
 * so the children of a different dimension are returned under the winning dimension's name.
 */
public class TestTaxonomyGetTopDimsStaleOrd extends LuceneTestCase {

  private void check(boolean requireDimCount) throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig());
    FacetsConfig config = new FacetsConfig();
    for (String dim : new String[] {"a", "b", "c"}) {
      config.setMultiValued(dim, true);
      config.setRequireDimCount(dim, requireDimCount);
    }
    // Same number of hits per dim, so the dims tie on value and the dim name breaks the tie.
    // Dims are added a, b, c, so the taxonomy visits them c, b, a (youngest child first).
    Document doc = new Document();
    doc.add(new FacetField("a", "a1"));
    doc.add(new FacetField("b", "b1"));
    doc.add(new FacetField("c", "c1"));
    iw.addDocument(config.build(taxoWriter, doc));

    DirectoryReader r = DirectoryReader.open(iw);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    IndexSearcher searcher = newSearcher(r);
    FacetsCollector fc =
        searcher.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);

    List<FacetResult> allDims = facets.getAllDims(10);
    assertEquals(3, allDims.size());
    assertEquals("a", allDims.get(0).dim);
    assertEquals("a1", allDims.get(0).labelValues[0].label);

    List<FacetResult> topDims = facets.getTopDims(1, 10);
    // Contract (Facets#getTopDims javadoc): same as getAllDims(10).subList(0, 1)
    assertEquals("getTopDims(1, 10) must equal getAllDims(10).subList(0, 1)",
        allDims.subList(0, 1), topDims);

    iw.close();
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir);
  }

  public void testMultiValuedRequireDimCount() throws Exception {
    check(true);
  }

  public void testMultiValuedNoDimCount() throws Exception {
    check(false);
  }
}
