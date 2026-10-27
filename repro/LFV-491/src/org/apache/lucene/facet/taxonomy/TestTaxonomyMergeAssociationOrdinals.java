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

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TaxonomyMergeUtils.merge re-maps the source index's taxonomy ordinals through
 * OrdinalMappingLeafReader, which only re-maps sorted-numeric doc values. Association facet fields
 * (IntAssociationFacetField, FloatAssociationFacetField) keep their ordinals in binary doc values,
 * so after the merge their values point at whatever categories the destination taxonomy has under
 * the source's ordinals. Public API only.
 */
public class TestTaxonomyMergeAssociationOrdinals extends LuceneTestCase {

  private static String str(FacetResult r) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    for (LabelAndValue lv : r.labelValues) {
      sb.append(lv.label).append('=').append(lv.value).append(' ');
    }
    return sb.toString().trim();
  }

  public void testMergeKeepsAssociationsOnTheirCategories() throws Exception {
    try (Directory srcIndex = newDirectory();
        Directory srcTaxo = newDirectory();
        Directory dstIndex = newDirectory();
        Directory dstTaxo = newDirectory()) {
      FacetsConfig config = new FacetsConfig();
      config.setIndexFieldName("a", "$a"); // association dims need their own index field
      config.setIndexFieldName("b", "$a");

      // source: dim "a" with int associations; ordinals root=0, a=1, a/x=2, a/y=3
      try (IndexWriter w =
              new IndexWriter(srcIndex, new IndexWriterConfig(new MockAnalyzer(random())));
          DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(srcTaxo)) {
        Document d = new Document();
        d.add(new IntAssociationFacetField(5, "a", "x"));
        w.addDocument(config.build(tw, d));
        d = new Document();
        d.add(new IntAssociationFacetField(7, "a", "y"));
        w.addDocument(config.build(tw, d));
      }

      // destination already holds dim "b": root=0, b=1, b/1=2, b/2=3
      IndexWriter dw = new IndexWriter(dstIndex, new IndexWriterConfig(new MockAnalyzer(random())));
      DirectoryTaxonomyWriter dtw = new DirectoryTaxonomyWriter(dstTaxo);
      Document d = new Document();
      d.add(new IntAssociationFacetField(100, "b", "1"));
      dw.addDocument(config.build(dtw, d));
      d = new Document();
      d.add(new IntAssociationFacetField(200, "b", "2"));
      dw.addDocument(config.build(dtw, d));

      TaxonomyMergeUtils.merge(
          srcIndex, srcTaxo, new DirectoryTaxonomyWriter.MemoryOrdinalMap(), dw, dtw, config);
      dw.close();
      dtw.close();

      try (DirectoryReader reader = DirectoryReader.open(dstIndex);
          DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(dstTaxo)) {
        IndexSearcher searcher = newSearcher(reader);
        FacetsCollector fc = new FacetsCollector();
        searcher.search(new MatchAllDocsQuery(), fc);
        TaxonomyFacetIntAssociations facets =
            new TaxonomyFacetIntAssociations(
                "$a", taxoReader, config, fc, AssociationAggregationFunction.SUM);
        String a = str(facets.getTopChildren(10, "a"));
        String b = str(facets.getTopChildren(10, "b"));
        String msg = "after merge: a=[" + a + "] b=[" + b + "]";
        assertEquals(msg, "y=7 x=5", a);
        assertEquals(msg, "2=200 1=100", b);
      }
    }
  }
}
