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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.sortedset.ConcurrentSortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.facet.taxonomy.AssociationAggregationFunction;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.FloatAssociationFacetField;
import org.apache.lucene.facet.taxonomy.IntAssociationFacetField;
import org.apache.lucene.facet.taxonomy.TaxonomyFacetFloatAssociations;
import org.apache.lucene.facet.taxonomy.TaxonomyFacetIntAssociations;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
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
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;

/**
 * Version-history variant of the getTopDims "dim without hits" repro. It compiles unchanged
 * against every release from 9.2.0 (the first release with Facets#getTopDims) to main.
 *
 * <p>Data: two flat dims "a" and "b". One doc (content=hit) has b=b1, one doc (content=miss) has
 * a=a1. The query content:hit matches only the b doc, so dim "a" has no hits.
 *
 * <p>The testTopDims* tests check the Facets#getTopDims contract ("Results should be the same as
 * calling getAllDims and then only using the first topNDims") plus "no null element". The
 * testGetAllDims* tests separately check the getAllDims contract ("labels for any dimension that
 * had hits"), so a release where getAllDims itself lists the hit-less dim shows up as a different
 * failing test. The testControl* tests are expected to pass on every release. Every case prints
 * one "OBS" line with what getAllDims and getTopDims returned.
 */
@SuppressSysoutChecks(bugUrl = "prints one observation line per case")
public class TestGetTopDimsNoHitDimHistory extends LuceneTestCase {

  private enum Kind {
    TAXO_COUNTS,
    TAXO_INT_ASSOC,
    TAXO_FLOAT_ASSOC,
    SSDV,
    SSDV_CONCURRENT
  }

  private static FacetsConfig newConfig(Kind kind, boolean multiValued, boolean requireDimCount) {
    FacetsConfig config = new FacetsConfig();
    for (String dim : new String[] {"a", "b"}) {
      if (multiValued) {
        config.setMultiValued(dim, true);
      }
      if (requireDimCount) {
        config.setRequireDimCount(dim, true);
      }
      if (kind == Kind.TAXO_INT_ASSOC) {
        config.setIndexFieldName(dim, "$facets.int");
      } else if (kind == Kind.TAXO_FLOAT_ASSOC) {
        config.setIndexFieldName(dim, "$facets.float");
      }
    }
    return config;
  }

  private static void addFacet(Kind kind, Document d, String dim, String label) {
    switch (kind) {
      case TAXO_COUNTS:
        d.add(new FacetField(dim, label));
        break;
      case TAXO_INT_ASSOC:
        d.add(new IntAssociationFacetField(dim.equals("a") ? 5 : 7, dim, label));
        break;
      case TAXO_FLOAT_ASSOC:
        d.add(new FloatAssociationFacetField(dim.equals("a") ? 5f : 7f, dim, label));
        break;
      case SSDV:
      case SSDV_CONCURRENT:
        d.add(new SortedSetDocValuesFacetField(dim, label));
        break;
    }
  }

  private static String render(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      if (r == null) {
        out.add("null");
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append(r.dim).append("(value=").append(r.value).append(", childCount=");
        sb.append(r.childCount).append(", children=[");
        for (int i = 0; i < r.labelValues.length; i++) {
          if (i > 0) {
            sb.append(' ');
          }
          sb.append(r.labelValues[i].label).append(':').append(r.labelValues[i].value);
        }
        sb.append("])");
        out.add(sb.toString());
      }
    }
    return out.toString();
  }

  private static List<String> dims(List<FacetResult> results) {
    List<String> out = new ArrayList<>();
    for (FacetResult r : results) {
      out.add(r == null ? null : r.dim);
    }
    return out;
  }

  private interface Check {
    void check(String label, Facets facets) throws IOException;
  }

  /** getTopDims(topNDims, 10) must equal getAllDims(10).subList(0, topNDims) and hold no null. */
  private static Check topDimsCheck(int topNDims) {
    return (label, facets) -> {
      List<FacetResult> all = facets.getAllDims(10);
      List<FacetResult> expected = all.subList(0, Math.min(topNDims, all.size()));
      List<FacetResult> actual = facets.getTopDims(topNDims, 10);
      String obs =
          label
              + ": getAllDims(10)="
              + render(all)
              + " getTopDims("
              + topNDims
              + ",10)="
              + render(actual);
      System.out.println("OBS " + obs);
      assertFalse("null element in getTopDims; " + obs, actual.contains(null));
      assertEquals("getTopDims dims != getAllDims prefix dims; " + obs, dims(expected), dims(actual));
      assertEquals("getTopDims != getAllDims prefix; " + obs, expected, actual);
    };
  }

  /** getAllDims(10) must list only the dim that had hits ("b"). */
  private static final Check ALL_DIMS_ONLY_HIT_DIMS =
      (label, facets) -> {
        List<FacetResult> all = facets.getAllDims(10);
        String obs = label + ": getAllDims(10)=" + render(all);
        System.out.println("OBS " + obs);
        assertEquals("getAllDims lists a dim without hits; " + obs, List.of("b"), dims(all));
      };

  private void run(Kind kind, boolean multiValued, boolean requireDimCount, Check check)
      throws Exception {
    String label =
        kind
            + (multiValued ? " multiValued" : " singleValued")
            + (requireDimCount ? " requireDimCount" : " noDimCount");
    FacetsConfig config = newConfig(kind, multiValued, requireDimCount);
    boolean taxo = kind != Kind.SSDV && kind != Kind.SSDV_CONCURRENT;
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
          DirectoryTaxonomyWriter tw = taxo ? new DirectoryTaxonomyWriter(taxoDir) : null) {
        // b first, then a: in the taxonomy "a" becomes the youngest child of the root and is
        // visited first by getTopDims, so a top-1 query never takes the replace-top branch.
        Document d1 = new Document();
        d1.add(new StringField("content", "hit", Field.Store.NO));
        addFacet(kind, d1, "b", "b1");
        w.addDocument(taxo ? config.build(tw, d1) : config.build(d1));
        Document d2 = new Document();
        d2.add(new StringField("content", "miss", Field.Store.NO));
        addFacet(kind, d2, "a", "a1");
        w.addDocument(taxo ? config.build(tw, d2) : config.build(d2));
        w.commit();
        if (taxo) {
          tw.commit();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        FacetsCollector fc =
            s.search(new TermQuery(new Term("content", "hit")), new FacetsCollectorManager());
        if (taxo) {
          try (TaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
            Facets facets;
            if (kind == Kind.TAXO_COUNTS) {
              facets = new FastTaxonomyFacetCounts(tr, config, fc);
            } else if (kind == Kind.TAXO_INT_ASSOC) {
              facets =
                  new TaxonomyFacetIntAssociations(
                      "$facets.int", tr, config, fc, AssociationAggregationFunction.SUM);
            } else {
              facets =
                  new TaxonomyFacetFloatAssociations(
                      "$facets.float", tr, config, fc, AssociationAggregationFunction.SUM);
            }
            check.check(label, facets);
          }
        } else {
          SortedSetDocValuesReaderState state =
              new DefaultSortedSetDocValuesReaderState(r, config);
          if (kind == Kind.SSDV_CONCURRENT) {
            ExecutorService exec = Executors.newFixedThreadPool(2);
            try {
              check.check(label, new ConcurrentSortedSetDocValuesFacetCounts(state, fc, exec));
            } finally {
              exec.shutdown();
              assertTrue(exec.awaitTermination(30, TimeUnit.SECONDS));
            }
          } else {
            check.check(label, new SortedSetDocValuesFacetCounts(state, fc));
          }
        }
      }
    }
  }

  // ---------- the bug: getTopDims vs getAllDims, multi-valued dims without dim counts ----------

  public void testTopDimsTaxonomyCountsTop10() throws Exception {
    run(Kind.TAXO_COUNTS, true, false, topDimsCheck(10));
  }

  public void testTopDimsTaxonomyCountsTop1() throws Exception {
    run(Kind.TAXO_COUNTS, true, false, topDimsCheck(1));
  }

  public void testTopDimsTaxonomyIntAssocTop10() throws Exception {
    run(Kind.TAXO_INT_ASSOC, true, false, topDimsCheck(10));
  }

  public void testTopDimsTaxonomyFloatAssocTop10() throws Exception {
    run(Kind.TAXO_FLOAT_ASSOC, true, false, topDimsCheck(10));
  }

  public void testTopDimsSortedSetTop10() throws Exception {
    run(Kind.SSDV, true, false, topDimsCheck(10));
  }

  public void testTopDimsSortedSetTop1() throws Exception {
    run(Kind.SSDV, true, false, topDimsCheck(1));
  }

  public void testTopDimsConcurrentSortedSetTop10() throws Exception {
    run(Kind.SSDV_CONCURRENT, true, false, topDimsCheck(10));
  }

  public void testTopDimsConcurrentSortedSetTop1() throws Exception {
    run(Kind.SSDV_CONCURRENT, true, false, topDimsCheck(1));
  }

  // ---------- separate contract: getAllDims lists only dims that had hits ----------

  public void testGetAllDimsTaxonomyCounts() throws Exception {
    run(Kind.TAXO_COUNTS, true, false, ALL_DIMS_ONLY_HIT_DIMS);
  }

  public void testGetAllDimsTaxonomyFloatAssoc() throws Exception {
    run(Kind.TAXO_FLOAT_ASSOC, true, false, ALL_DIMS_ONLY_HIT_DIMS);
  }

  public void testGetAllDimsSortedSet() throws Exception {
    run(Kind.SSDV, true, false, ALL_DIMS_ONLY_HIT_DIMS);
  }

  // ---------- controls (expected to pass on every release) ----------

  /** Same data, multi-valued dims that require dim counts: the real dim count 0 is skipped. */
  public void testControlRequireDimCount() throws Exception {
    for (int topN : new int[] {10, 1}) {
      run(Kind.TAXO_COUNTS, true, true, topDimsCheck(topN));
      run(Kind.SSDV, true, true, topDimsCheck(topN));
      run(Kind.SSDV_CONCURRENT, true, true, topDimsCheck(topN));
    }
  }

  /** Same data, single-valued dims: the dim value is aggregated from children, so it is 0. */
  public void testControlSingleValued() throws Exception {
    for (int topN : new int[] {10, 1}) {
      run(Kind.TAXO_COUNTS, false, false, topDimsCheck(topN));
      run(Kind.SSDV, false, false, topDimsCheck(topN));
      run(Kind.SSDV_CONCURRENT, false, false, topDimsCheck(topN));
    }
  }
}
