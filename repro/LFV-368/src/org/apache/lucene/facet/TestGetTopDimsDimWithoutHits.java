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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Facets#getTopDims must equal getAllDims(topNChildren).subList(0, topNDims). A multi-valued
 * dimension that does not require dim counts gets the placeholder value -1, which passes the "value
 * != 0" filter, so a dimension that has no hits for the query is admitted. Its FacetResult is then
 * null: getTopDims throws AssertionError with assertions enabled, and returns a list containing null
 * (and, for small topNDims, drops a dimension that does have hits) with assertions disabled.
 */
public class TestGetTopDimsDimWithoutHits extends LuceneTestCase {

  private static FacetsConfig config() {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued("a", true); // requireDimCount stays false (the default)
    config.setMultiValued("b", true);
    return config;
  }

  /** FacetResult#equals ignores dim and path, so compare those explicitly too. */
  private static String describe(List<FacetResult> results) {
    StringBuilder sb = new StringBuilder();
    for (FacetResult r : results) {
      sb.append(r == null ? "null" : r.dim + "=" + r.value + java.util.Arrays.toString(r.labelValues));
      sb.append(';');
    }
    return sb.toString();
  }

  private static void check(Facets facets, int topNDims) throws Exception {
    List<FacetResult> all = facets.getAllDims(10);
    assertEquals(1, all.size());
    assertEquals("b", all.get(0).dim);
    List<FacetResult> expected = all.subList(0, Math.min(topNDims, all.size()));
    assertEquals(
        "getTopDims(" + topNDims + ", 10) must equal getAllDims(10).subList(0, " + topNDims + ")",
        describe(expected),
        describe(facets.getTopDims(topNDims, 10)));
  }

  public void testTaxonomy() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    FacetsConfig config = config();
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new FacetField("a", "a1"));
    w.addDocument(config.build(tw, doc));
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new FacetField("b", "b1"));
    w.addDocument(config.build(tw, doc));
    DirectoryReader r = DirectoryReader.open(w);
    w.close();
    DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    FacetsCollector fc =
        new IndexSearcher(r).search(new TermQuery(new Term("id", "1")), new FacetsCollectorManager());
    // topNDims=10: no priority-queue replacement happens, so only the hit-less dim matters here
    check(new FastTaxonomyFacetCounts(tr, config, fc), 10);
    IOUtils.close(r, tr, tw, dir, taxoDir);
  }

  public void testSortedSetDocValues() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    FacetsConfig config = config();
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new SortedSetDocValuesFacetField("a", "a1"));
    w.addDocument(config.build(doc));
    doc = new Document();
    doc.add(new StringField("id", "1", Field.Store.NO));
    doc.add(new SortedSetDocValuesFacetField("b", "b1"));
    w.addDocument(config.build(doc));
    DirectoryReader r = DirectoryReader.open(w);
    w.close();
    FacetsCollector fc =
        new IndexSearcher(r).search(new TermQuery(new Term("id", "1")), new FacetsCollectorManager());
    Facets facets =
        new SortedSetDocValuesFacetCounts(new DefaultSortedSetDocValuesReaderState(r, config), fc);
    check(facets, 10);
    // "a" sorts before "b", so with topNDims=1 the hit-less dim "a" wins the tie at -1 and the
    // dimension that has hits is dropped:
    check(facets, 1);
    IOUtils.close(r, dir);
  }
}
