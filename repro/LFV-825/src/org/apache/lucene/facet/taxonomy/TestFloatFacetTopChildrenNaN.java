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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
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
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Labels "a", "b", "c" are indexed in that order; "a" aggregates to 5, "b" to NaN and "c" to 10.
 * Whatever rank NaN gets, the top-1 label is "c" (NaN ranked last) or "b" (NaN ranked first, as
 * Float.compare does); it is never "a", because "c" outranks it.
 */
public class TestFloatFacetTopChildrenNaN extends LuceneTestCase {

  private interface DocMaker {
    void add(Document doc, int label);
  }

  private interface FacetsMaker {
    Facets make(DirectoryTaxonomyReader tr, FacetsConfig config, FacetsCollector fc)
        throws Exception;
  }

  private void checkTop1(int docsPerLabel, DocMaker docs, FacetsMaker maker) throws Exception {
    String[] labels = {"a", "b", "c"};
    try (Directory dir = newDirectory();
        Directory taxoDir = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())));
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir)) {
        for (int i = 0; i < labels.length; i++) {
          for (int j = 0; j < docsPerLabel; j++) {
            Document doc = new Document();
            docs.add(doc, i * docsPerLabel + j);
            w.addDocument(config.build(tw, doc));
          }
        }
        w.commit();
        tw.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          DirectoryTaxonomyReader tr = new DirectoryTaxonomyReader(taxoDir)) {
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = maker.make(tr, config, fc);
        FacetResult all = facets.getAllChildren("dim");
        FacetResult top1 = facets.getTopChildren(1, "dim");
        String got = top1.labelValues[0].label;
        assertTrue(
            "getTopChildren(1) returned "
                + Arrays.toString(top1.labelValues)
                + ", but c outranks a; all children: "
                + Arrays.toString(all.labelValues),
            got.equals("c") || got.equals("b"));
      }
    }
  }

  /** Float associations a=5, b=NaN, c=10 (FloatAssociationFacetField accepts any float). */
  public void testFloatAssociationNaN() throws Exception {
    float[] values = {5f, Float.NaN, 10f};
    String[] labels = {"a", "b", "c"};
    checkTop1(
        1,
        (doc, i) -> doc.add(new FloatAssociationFacetField(values[i], "dim", labels[i])),
        (tr, config, fc) ->
            new TaxonomyFacetFloatAssociations(
                tr, config, fc, AssociationAggregationFunction.SUM));
  }

  /**
   * No NaN in the index: the weight is the expression sqrt(popularity), as in the facet module's
   * expression-aggregation demo, and "b" has popularity -1 (a=25, c=100).
   */
  public void testExpressionValueSourceNaN() throws Exception {
    long[] popularity = {25, -1, 100};
    String[] labels = {"a", "b", "c"};
    Expression expr = JavascriptCompiler.compile("sqrt(popularity)");
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("popularity", DoubleValuesSource.fromLongField("popularity"));
    checkTop1(
        1,
        (doc, i) -> {
          doc.add(new FacetField("dim", labels[i]));
          doc.add(new NumericDocValuesField("popularity", popularity[i]));
        },
        (tr, config, fc) ->
            new TaxonomyFacetFloatAssociations(
                tr,
                config,
                fc,
                AssociationAggregationFunction.SUM,
                expr.getDoubleValuesSource(bindings)));
  }

  /**
   * No NaN in the index either: finite double weights beyond the float range. "b" has two docs,
   * 1e39 and -1e39, which TaxonomyFacetFloatAssociations narrows to +Inf and -Inf and sums to NaN.
   */
  public void testDoubleWeightsBeyondFloatRangeSumToNaN() throws Exception {
    double[] weights = {2.5, 2.5, 1e39, -1e39, 5, 5};
    String[] labels = {"a", "b", "c"};
    checkTop1(
        2,
        (doc, i) -> {
          doc.add(new FacetField("dim", labels[i / 2]));
          doc.add(new DoubleDocValuesField("weight", weights[i]));
        },
        (tr, config, fc) ->
            new TaxonomyFacetFloatAssociations(
                tr,
                config,
                fc,
                AssociationAggregationFunction.SUM,
                DoubleValuesSource.fromDoubleField("weight")));
  }
}
