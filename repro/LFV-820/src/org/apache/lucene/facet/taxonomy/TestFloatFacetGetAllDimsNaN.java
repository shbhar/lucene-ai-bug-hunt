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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Three dims: "a" = 1, "b" = NaN, "c" = 2 (one float association each). */
public class TestFloatFacetGetAllDimsNaN extends LuceneTestCase {

  /** 48 dims, two of them NaN (found by a seeded search, then minimised). */
  private static final float[] MANY_VALUES = {
    1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, Float.NaN, 3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
    1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, Float.NaN, 2f, 1f, 1f,
    2f, 1f, 2f, 2f
  };

  private static final String[] MANY_DIMS = {
    "dimgf75", "dimecmr", "dimgu55", "dim7sg", "dim7tt9", "dimb0xw", "dimk5z3", "dimgotg",
    "dimfwz5", "dimcsho", "dim7wq9", "dimles4", "dim2poo", "dim5ofb", "dimj3ni", "dimfnla",
    "dim1vby", "dimffl8", "dimkvfq", "dim2gfu", "dimef4e", "dim2lv8", "dimhmxs", "dimgoe9",
    "dimd8ix", "dimfnnn", "dimamod", "dimd9ux", "dim5puk", "dimdgwq", "dim8ll0", "dime101",
    "dimhbk6", "dimdyjo", "dim4xyg", "dimg11v", "dim7196", "dimixav", "dimjh7e", "dimfdqe",
    "dim2bnp", "dimlw78", "dim9wze", "dim69wa", "dimbqtm", "dimddau", "dim2bf1", "dimkwlm"
  };

  private interface Check {
    void run(Facets facets) throws Exception;
  }

  private void withFacets(Check check) throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      facets(dir, taxoDir, check);
    }
  }

  private Facets facets(Directory dir, Directory taxoDir, Check check) throws Exception {
    return facets(dir, taxoDir, new float[] {1f, Float.NaN, 2f}, new String[] {"a", "b", "c"}, check);
  }

  private Facets facets(
      Directory dir, Directory taxoDir, float[] values, String[] dims, Check check)
      throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())));
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
      for (int i = 0; i < dims.length; i++) {
        Document doc = new Document();
        doc.add(new FloatAssociationFacetField(values[i], dims[i], "x"));
        w.addDocument(config.build(tw, doc));
      }
      w.commit();
      tw.commit();
    }
    try (DirectoryReader r = DirectoryReader.open(dir);
        DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
      FacetsCollector fc =
          new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      Facets facets =
          new TaxonomyFacetFloatAssociations(tr, config, fc, AssociationAggregationFunction.SUM);
      check.run(facets);
      return null;
    }
  }

  private static List<String> dims(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      out.add(r.dim + "=" + r.value);
    }
    return out;
  }

  /** Facets#getAllDims: "sorted by the number of hits that dimension matched" (here: by value). */
  public void testGetAllDimsOrdersNonNaNDims() throws Exception {
    withFacets(
        facets -> {
          List<String> all = dims(facets.getAllDims(10));
          assertTrue(
              "getAllDims lists a=1.0 before c=2.0: " + all,
              all.indexOf("c=2.0") < all.indexOf("a=1.0"));
        });
  }

  /**
   * Facets#getTopDims: "Results should be the same as calling getAllDims and then only using the
   * first topNDims."
   */
  public void testGetTopDimsIsPrefixOfGetAllDims() throws Exception {
    withFacets(
        facets -> {
          List<String> all = dims(facets.getAllDims(10));
          List<String> top1 = dims(facets.getTopDims(1, 10));
          assertEquals("getAllDims=" + all, all.subList(0, 1), top1);
        });
  }

  /** With many dims (the "sparse" faceting case getAllDims is documented for) the sort throws. */
  public void testGetAllDimsWithManyDimsDoesNotThrow() throws Exception {
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      facets(
          dir,
          taxoDir,
          MANY_VALUES,
          MANY_DIMS,
          facets -> {
            List<FacetResult> all = facets.getAllDims(1);
            assertEquals(MANY_DIMS.length, all.size());
          });
    }
  }
}
