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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Facets#getAllDims: "Returns topN labels for any dimension that had hits, sorted by the number of
 * hits that dimension matched"; Facets#getTopDims: "Results should be the same as calling
 * getAllDims and then only using the first topNDims". MultiFacets (which DrillSideways returns by
 * default) lists its mapped dimensions first, unsorted, and includes null for a mapped dimension
 * without hits.
 */
public class TestMultiFacetsAllDimsContract extends LuceneTestCase {

  private Directory dir, taxoDir;
  private DirectoryReader reader;
  private TaxonomyReader taxoReader;
  private IndexSearcher searcher;
  private final FacetsConfig config = new FacetsConfig();

  private void addDoc(IndexWriter w, DirectoryTaxonomyWriter tw, String author, String year)
      throws Exception {
    Document doc = new Document();
    doc.add(new StringField("content", "foo", Field.Store.NO));
    if (author != null) doc.add(new FacetField("Author", author));
    if (year != null) doc.add(new FacetField("Publish Date", year));
    w.addDocument(config.build(tw, doc));
  }

  private void open(String[][] docs) throws Exception {
    dir = newDirectory();
    taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    for (String[] d : docs) addDoc(w, tw, d[0], d[1]);
    reader = DirectoryReader.open(w);
    taxoReader = new DirectoryTaxonomyReader(tw);
    searcher = new IndexSearcher(reader);
    IOUtils.close(w, tw);
  }

  @Override
  public void tearDown() throws Exception {
    IOUtils.close(reader, taxoReader, dir, taxoDir);
    super.tearDown();
  }

  private static void assertSortedByValueWithoutNulls(List<FacetResult> results) {
    for (int i = 0; i < results.size(); i++) {
      assertNotNull("getAllDims entry " + i + " is null: " + results, results.get(i));
      if (i > 0) {
        assertTrue(
            "getAllDims is not sorted by value: " + results,
            results.get(i - 1).value.doubleValue() >= results.get(i).value.doubleValue());
      }
    }
  }

  /** MultiFacets over the same counts as the default Facets must give the same getAllDims. */
  public void testMappedDimIsNotSorted() throws Exception {
    open(
        new String[][] {
          {"Lisa", "2010"}, {null, "2010"}, {null, "2012"},
        });
    FacetsCollector fc =
        searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    Facets counts = new FastTaxonomyFacetCounts(taxoReader, config, fc);
    Map<String, Facets> byDim = new LinkedHashMap<>();
    byDim.put("Author", new FastTaxonomyFacetCounts(taxoReader, config, fc));
    Facets multi = new MultiFacets(byDim, counts);

    // Publish Date has 3 hits, Author 1:
    assertEquals("Publish Date", counts.getAllDims(10).get(0).dim);
    List<FacetResult> all = multi.getAllDims(10);
    assertSortedByValueWithoutNulls(all); // fails: [Author (1), Publish Date (3)]
    assertEquals("Publish Date", multi.getTopDims(1, 10).get(0).dim);
  }

  /** A mapped dimension without hits must not show up (as null) in getAllDims. */
  public void testMappedDimWithoutHitsIsNull() throws Exception {
    open(new String[][] {{null, "2010"}, {"Lisa", null}});
    FacetsCollector fc =
        searcher.search(
            new TermQuery(new Term("content", "no-such-term")), new FacetsCollectorManager());
    Map<String, Facets> byDim = new LinkedHashMap<>();
    byDim.put("Author", new FastTaxonomyFacetCounts(taxoReader, config, fc));
    Facets multi = new MultiFacets(byDim, new FastTaxonomyFacetCounts(taxoReader, config, fc));
    assertEquals(List.of(), new FastTaxonomyFacetCounts(taxoReader, config, fc).getAllDims(10));
    List<FacetResult> all = multi.getAllDims(10);
    assertSortedByValueWithoutNulls(all); // fails: [null]
  }

  /** The same through DrillSideways, whose default result is a MultiFacets. */
  public void testDrillSidewaysGetAllDims() throws Exception {
    // Two orientations, so that one of them is out of order whatever the HashMap order of the
    // two sideways dimensions is.
    for (boolean authorBigger : new boolean[] {true, false}) {
      String[][] docs =
          authorBigger
              ? new String[][] {
                {"Lisa", "2010"}, {"Bob", "2010"}, {"Frank", "2010"}, {"Susan", "2012"},
              }
              : new String[][] {
                {"Lisa", "2010"}, {"Lisa", "2011"}, {"Lisa", "2012"}, {"Bob", "2013"},
              };
      open(docs);
      DrillDownQuery ddq = new DrillDownQuery(config);
      ddq.add("Author", "Lisa");
      ddq.add("Publish Date", "2010");
      DrillSideways ds = new DrillSideways(searcher, config, taxoReader);
      DrillSideways.DrillSidewaysResult r = ds.search(ddq, 10);
      assertEquals(1, r.hits.scoreDocs.length);
      // sideways counts: Author over Publish Date=2010, Publish Date over Author=Lisa
      int author = r.facets.getTopChildren(10, "Author").value.intValue();
      int date = r.facets.getTopChildren(10, "Publish Date").value.intValue();
      assertEquals(authorBigger ? 3 : 1, author);
      assertEquals(authorBigger ? 1 : 3, date);
      List<FacetResult> all = r.facets.getAllDims(10);
      assertEquals(2, all.size());
      assertSortedByValueWithoutNulls(all);
      assertEquals(authorBigger ? "Author" : "Publish Date", r.facets.getTopDims(1, 10).get(0).dim);
      tearDownIndex();
    }
  }

  /** DrillSideways for a query without hits: getAllDims must not contain null. */
  public void testDrillSidewaysNoHits() throws Exception {
    open(new String[][] {{"Lisa", "2010"}, {"Bob", "2012"}});
    DrillDownQuery ddq =
        new DrillDownQuery(config, new TermQuery(new Term("content", "no-such-term")));
    ddq.add("Author", "Lisa");
    DrillSideways ds = new DrillSideways(searcher, config, taxoReader);
    DrillSideways.DrillSidewaysResult r = ds.search(ddq, 10);
    assertEquals(0, r.hits.scoreDocs.length);
    List<FacetResult> all = r.facets.getAllDims(10);
    assertSortedByValueWithoutNulls(all); // fails: [null]
  }

  private void tearDownIndex() throws Exception {
    IOUtils.close(reader, taxoReader, dir, taxoDir);
    reader = null;
    taxoReader = null;
    dir = null;
    taxoDir = null;
  }
}
