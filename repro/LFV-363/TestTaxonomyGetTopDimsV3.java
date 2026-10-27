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
 * Devil's-advocate variants: no ties (the winning dim has strictly more hits), the documented
 * requireDimCount=true configuration, and a multi-valued dim that displaces a single-valued one.
 */
public class TestTaxonomyGetTopDimsV3 extends LuceneTestCase {

  private void run(boolean bMulti) throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter iw = new IndexWriter(indexDir, new IndexWriterConfig());
    FacetsConfig config = new FacetsConfig();
    // "a" is the documented configuration for getTopDims: multi-valued with dim counts.
    config.setMultiValued("a", true);
    config.setRequireDimCount("a", true);
    if (bMulti) {
      config.setMultiValued("b", true);
      config.setRequireDimCount("b", true);
    }
    // a is created first and has 3 hits; b is created second and has 1 hit (no tie).
    for (int i = 0; i < 3; i++) {
      Document doc = new Document();
      doc.add(new FacetField("a", "a" + i));
      if (i == 0) {
        doc.add(new FacetField("b", "b1"));
      }
      iw.addDocument(config.build(taxoWriter, doc));
    }
    DirectoryReader r = DirectoryReader.open(iw);
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    IndexSearcher searcher = newSearcher(r);
    FacetsCollector fc = searcher.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    Facets facets = new FastTaxonomyFacetCounts(taxoReader, config, fc);

    List<FacetResult> allDims = facets.getAllDims(10);
    FacetResult topA = facets.getTopChildren(10, "a");
    assertEquals("a", allDims.get(0).dim);
    assertEquals(3, allDims.get(0).value.intValue());
    List<FacetResult> topDims = facets.getTopDims(1, 10);
    System.out.println("bMulti=" + bMulti + " getAllDims(10)=" + allDims);
    System.out.println("bMulti=" + bMulti + " getTopChildren(10,a)=" + topA);
    System.out.println("bMulti=" + bMulti + " getTopDims(1,10)=" + topDims);
    // equals() ignores dim/path, so compare the full string form too
    assertEquals(topA.toString(), topDims.get(0).toString());
    assertEquals(allDims.subList(0, 1), topDims);
    iw.close();
    IOUtils.close(taxoWriter, taxoReader, taxoDir, r, indexDir);
  }

  public void testNoTieBothMultiValued() throws Exception {
    run(true);
  }

  public void testNoTieMultiDisplacesSingle() throws Exception {
    run(false);
  }
}
