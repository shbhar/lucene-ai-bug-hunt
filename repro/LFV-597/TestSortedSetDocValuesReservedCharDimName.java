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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * A flat SSDV dimension that is multiValued and requireDimCount must count correctly when its name
 * contains FacetsConfig's delimiter (U+001F) or escape (U+001E) character, as taxonomy facets and
 * the other SSDV configurations do. Documents: {x, y}, {x}, {z}.
 */
public class TestSortedSetDocValuesReservedCharDimName extends LuceneTestCase {

  private static final String EXPECTED = "value=3 childCount=3 x=2 y=1 z=1 dims=";

  /** Shows control characters as Java escapes, so failure messages stay readable. */
  private static String show(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(c < 0x20 ? String.format(Locale.ROOT, "\\u%04X", (int) c) : String.valueOf(c));
    }
    return sb.toString();
  }

  private static String facetCounts(String dim) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(dim, true);
    config.setRequireDimCount(dim, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String[] labels : new String[][] {{"x", "y"}, {"x"}, {"z"}}) {
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
        SortedSetDocValuesReaderState state =
            new DefaultSortedSetDocValuesReaderState(reader, config);
        Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
        FacetResult r = facets.getTopChildren(10, dim);
        StringBuilder sb = new StringBuilder("value=" + r.value + " childCount=" + r.childCount);
        for (LabelAndValue lv : r.labelValues) {
          sb.append(' ').append(lv.label).append('=').append(lv.value);
        }
        List<String> dims = new ArrayList<>();
        for (FacetResult d : facets.getAllDims(10)) {
          dims.add(d.dim);
        }
        return show(sb + " dims=" + dims);
      }
    }
  }

  private static void check(String dim) throws Exception {
    assertEquals(show(EXPECTED + List.of(dim)), facetCounts(dim));
  }

  public void testPlainName() throws Exception {
    check("color"); // control: passes
  }

  public void testNameWithDelimiter() throws Exception {
    check("color\u001Fcode");
  }

  public void testNameWithEscapeChar() throws Exception {
    check("color\u001Ecode");
  }

  public void testNameEndingWithEscapeChar() throws Exception {
    check("color\u001E"); // with assertions on: AssertionError in FacetsConfig.stringToPath
  }

  public void testNameWithTwoDelimiters() throws Exception {
    check("a\u001Fb\u001Fc"); // IllegalArgumentException from the reader state
  }
}
