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

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * DrillSideways(searcher, config, taxoReader, state) is documented for "where some dimensions were
 * indexed with SortedSetDocValuesFacetField and others were indexed with FacetField", but its
 * default buildFacetsResult uses only the taxonomy when taxoReader != null and never the
 * SortedSetDocValuesReaderState, so the SortedSetDocValues dimensions are never counted.
 */
public class TestDrillSidewaysMixedFacets extends LuceneTestCase {

  public void testMixedTaxonomyAndSortedSetDims() throws Exception {
    FacetsConfig config = new FacetsConfig();
    // Taxonomy ordinals (SortedNumeric doc values) and SSDV ordinals (SortedSet doc values) cannot
    // share one field, so the SSDV dimension gets its own index field:
    config.setIndexFieldName("size", "ssdv_facets");

    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    String[][] docs = {{"red", "small"}, {"red", "large"}, {"blue", "small"}};
    for (String[] d : docs) {
      Document doc = new Document();
      doc.add(new FacetField("color", d[0]));
      doc.add(new SortedSetDocValuesFacetField("size", d[1]));
      writer.addDocument(config.build(taxoWriter, doc));
    }
    DirectoryReader reader = DirectoryReader.open(writer);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoWriter);
    IndexSearcher searcher = newSearcher(reader);
    SortedSetDocValuesReaderState state =
        new DefaultSortedSetDocValuesReaderState(reader, "ssdv_facets", config);

    DrillSideways ds = new DrillSideways(searcher, config, taxoReader, state);
    DrillDownQuery q = new DrillDownQuery(config);
    q.add("color", "red");
    DrillSideways.DrillSidewaysResult r = ds.search(q, 10);
    assertEquals(2, r.hits.scoreDocs.length);

    // The collectors are right: counting them with the matching Facets gives the expected values.
    FacetResult expectedColor =
        new FastTaxonomyFacetCounts(taxoReader, config, r.drillSidewaysFacetsCollector[0])
            .getTopChildren(10, "color");
    FacetResult expectedSize =
        new SortedSetDocValuesFacetCounts(state, r.drillDownFacetsCollector)
            .getTopChildren(10, "size");
    assertEquals("dim=color path=[] value=3 childCount=2\n  red (2)\n  blue (1)\n", expectedColor.toString());
    assertEquals("dim=size path=[] value=2 childCount=2\n  large (1)\n  small (1)\n", expectedSize.toString());

    // The result's Facets: sideways counts for the taxonomy dim, drill-down counts for the SSDV dim.
    assertEquals(expectedColor.toString(), r.facets.getTopChildren(10, "color").toString());
    String size;
    try {
      FacetResult fr = r.facets.getTopChildren(10, "size");
      size = String.valueOf(fr);
    } catch (IllegalArgumentException e) {
      size = e.toString();
    }
    IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
    assertEquals(expectedSize.toString(), size);
  }
}
