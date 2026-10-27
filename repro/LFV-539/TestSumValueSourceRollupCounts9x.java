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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/** 9.x only: the deprecated TaxonomyFacetSumValueSource takes the same value-source path. */
public class TestSumValueSourceRollupCounts9x extends LuceneTestCase {

  @SuppressWarnings("deprecation")
  public void testDeprecatedSumValueSourceCount() throws Exception {
    Directory dir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("d", true);
    long[] weights = {7, 2};
    String[] leaves = {"x", "y"};
    for (int i = 0; i < 2; i++) {
      Document doc = new Document();
      doc.add(new FacetField("d", "a", leaves[i]));
      doc.add(new NumericDocValuesField("w", weights[i]));
      w.addDocument(config.build(tw, doc));
    }
    DirectoryReader r = DirectoryReader.open(w);
    TaxonomyReader tr = new DirectoryTaxonomyReader(tw);
    try {
      FacetsCollector fc =
          newSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
      FacetResult top =
          new TaxonomyFacetSumValueSource(tr, config, fc, DoubleValuesSource.fromLongField("w"))
              .getTopChildren(10, "d");
      assertEquals(9f, top.labelValues[0].value.floatValue(), 0f);
      assertEquals(
          "TaxonomyFacetSumValueSource: value and count of d/a",
          List.of(9f, 2),
          List.of(top.labelValues[0].value.floatValue(), top.labelValues[0].count));
    } finally {
      IOUtils.close(r, tr, w, tw, dir, taxoDir);
    }
  }
}
