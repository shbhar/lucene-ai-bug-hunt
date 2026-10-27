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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
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
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * TaxonomyFacetFloatAssociations with a DoubleValuesSource on a hierarchical dimension should
 * report LabelAndValue#count as the number of matching documents under each label, as
 * FastTaxonomyFacetCounts does for the same hits. Public API only.
 */
public class TestFloatAssociationRollupCounts extends LuceneTestCase {

  /** Two documents under d/a: the sum is 7 + 2 = 9 and the count is 2. */
  public void testCountOfLabelWithChildren() throws Exception {
    try (Index index = new Index()) {
      index.add(7, "a", "x");
      index.add(2, "a", "y");
      index.open();

      FacetResult counts = index.counts().getTopChildren(10, "d");
      assertEquals("a", counts.labelValues[0].label);
      assertEquals(2, counts.labelValues[0].count);

      Facets sums = index.sums();
      FacetResult top = sums.getTopChildren(10, "d");
      FacetResult all = sums.getAllChildren("d");
      assertEquals("a", top.labelValues[0].label);
      assertEquals(9f, top.labelValues[0].value.floatValue(), 0f); // the value is right
      assertEquals(
          "count of d/a from getTopChildren and getAllChildren",
          List.of(2, 2),
          List.of(top.labelValues[0].count, all.labelValues[0].count));
    }
  }

  /** One document at d/a/b/x: each label on its path has one document. */
  public void testCountsAlongOneDeepPath() throws Exception {
    try (Index index = new Index()) {
      index.add(5, "a", "b", "x");
      index.open();

      Facets sums = index.sums();
      List<Integer> counts = new ArrayList<>();
      for (String[] path : new String[][] {{}, {"a"}, {"a", "b"}}) {
        FacetResult result = sums.getTopChildren(10, "d", path);
        assertEquals(5f, result.labelValues[0].value.floatValue(), 0f); // the value is right
        counts.add(result.labelValues[0].count);
      }
      assertEquals("counts of d/a, d/a/b and d/a/b/x", List.of(1, 1, 1), counts);
    }
  }

  /** One hierarchical, single-valued dimension "d"; each document has a weight "w". */
  private static final class Index implements Closeable {
    final Directory dir = newDirectory();
    final Directory taxoDir = newDirectory();
    final FacetsConfig config = new FacetsConfig();
    final IndexWriter writer;
    final DirectoryTaxonomyWriter taxoWriter;
    DirectoryReader reader;
    TaxonomyReader taxoReader;
    FacetsCollector hits;

    Index() throws IOException {
      config.setHierarchical("d", true);
      writer = new IndexWriter(dir, newIndexWriterConfig());
      taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    }

    void add(long weight, String... path) throws IOException {
      Document doc = new Document();
      doc.add(new FacetField("d", path));
      doc.add(new NumericDocValuesField("w", weight));
      writer.addDocument(config.build(taxoWriter, doc));
    }

    void open() throws IOException {
      reader = DirectoryReader.open(writer);
      taxoReader = new DirectoryTaxonomyReader(taxoWriter);
      hits = newSearcher(reader).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    }

    Facets counts() throws IOException {
      return new FastTaxonomyFacetCounts(taxoReader, config, hits);
    }

    Facets sums() throws IOException {
      return new TaxonomyFacetFloatAssociations(
          taxoReader,
          config,
          hits,
          AssociationAggregationFunction.SUM,
          DoubleValuesSource.fromLongField("w"));
    }

    @Override
    public void close() throws IOException {
      IOUtils.close(reader, taxoReader, writer, taxoWriter, dir, taxoDir);
    }
  }
}
