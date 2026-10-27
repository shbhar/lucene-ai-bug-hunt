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
package org.apache.lucene.facet.sortedset;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FacetsConfig escapes U+001E and U+001F inside every SortedSetDocValues facet path
 * (pathToString), except for the bare dimension term it adds for a flat dimension that is
 * multiValued and requireDimCount: that term is the raw dimension name. A dimension name that
 * contains U+001F or U+001E is then decoded as a different path.
 */
public class TestSsdvReservedCharInDimCountDim extends LuceneTestCase {

  private static String counts(FacetResult r) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("value=" + r.value + " childCount=" + r.childCount);
    for (LabelAndValue lv : r.labelValues) {
      sb.append(' ').append(lv.label).append('=').append(lv.value);
    }
    return sb.toString();
  }

  /** Docs: {x, y}, {x}, {z}. Expected for the dimension: value=3, x=2, y=1, z=1. */
  private void check(String dim) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(dim, true);
    config.setRequireDimCount(dim, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        String[][] docs = {{"x", "y"}, {"x"}, {"z"}};
        for (String[] labels : docs) {
          Document doc = new Document();
          for (String label : labels) {
            doc.add(new SortedSetDocValuesFacetField(dim, label));
          }
          w.addDocument(config.build(doc));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader, config);
        Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
        assertEquals("value=3 childCount=3 x=2 y=1 z=1", counts(facets.getTopChildren(10, dim)));
        List<FacetResult> all = facets.getAllDims(10);
        assertEquals("getAllDims must list exactly the one indexed dimension", 1, all.size());
        assertEquals(dim, all.get(0).dim);
      }
    }
  }

  public void testPlainDimension() throws Exception {
    check("color"); // control
  }

  public void testDimensionWithUnitSeparator() throws Exception {
    check("color\u001Fcode");
  }

  public void testDimensionWithRecordSeparator() throws Exception {
    check("color\u001Ecode");
  }

  public void testDimensionEndingWithRecordSeparator() throws Exception {
    check("color\u001E"); // AssertionError from FacetsConfig.stringToPath with -ea
  }
}
