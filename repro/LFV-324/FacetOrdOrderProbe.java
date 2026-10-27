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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** Prints the facet terms in ord order and the children SortedSetDocValues faceting reports. */
public class FacetOrdOrderProbe {

  public static void main(String[] args) throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("cat", true);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new SortedSetDocValuesFacetField("cat", "shoes", "red"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new SortedSetDocValuesFacetField("cat", "shoes\t"));
        w.addDocument(config.build(d2));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        SortedSetDocValues dv = state.getDocValues();
        System.out.println("facet terms in ord order (U+001F shown as /):");
        for (int ord = 0; ord < dv.getValueCount(); ord++) {
          System.out.println("  ord " + ord + ": " + show(dv.lookupOrd(ord).utf8ToString()));
        }
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
        print("getTopChildren(10, cat)", facets.getTopChildren(10, "cat"));
        print("getTopChildren(10, cat, shoes)", facets.getTopChildren(10, "cat", "shoes"));
        print("getTopChildren(10, cat, shoes<TAB>)", facets.getTopChildren(10, "cat", "shoes\t"));
        System.out.println(
            "getSpecificValue(cat, shoes, red) = " + facets.getSpecificValue("cat", "shoes", "red"));
      }
    }
    dimensions();
  }

  /** Two hierarchical dimensions, "color" and "color TAB". */
  private static void dimensions() throws Exception {
    FacetsConfig config = new FacetsConfig();
    config.setHierarchical("color", true);
    config.setHierarchical("color\t", true);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new SortedSetDocValuesFacetField("color", "red"));
        w.addDocument(config.build(d1));
        Document d2 = new Document();
        d2.add(new SortedSetDocValuesFacetField("color\t", "x"));
        w.addDocument(config.build(d2));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        SortedSetDocValues dv = state.getDocValues();
        System.out.println("dimensions color and color<TAB>, terms in ord order:");
        for (int ord = 0; ord < dv.getValueCount(); ord++) {
          System.out.println("  ord " + ord + ": " + show(dv.lookupOrd(ord).utf8ToString()));
        }
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
        print("getTopChildren(10, color)", facets.getTopChildren(10, "color"));
        print("getTopChildren(10, color<TAB>)", facets.getTopChildren(10, "color\t"));
        List<String> dims = new ArrayList<>();
        for (FacetResult result : facets.getAllDims(10)) {
          dims.add(show(result.dim));
        }
        System.out.println("getAllDims(10) dims = " + dims);
        try {
          List<String> top = new ArrayList<>();
          for (FacetResult result : facets.getTopDims(10, 10)) {
            top.add(result == null ? "null" : show(result.dim));
          }
          System.out.println("getTopDims(10, 10) dims = " + top);
        } catch (Throwable t) {
          System.out.println("getTopDims(10, 10) threw " + t);
        }
      }
    }
  }

  private static void print(String call, FacetResult result) {
    System.out.println(call + " = " + (result == null ? "null" : show(result.toString().trim())));
  }

  private static String show(String s) {
    return s.replace("\u001F", "/").replace("\t", "<TAB>").replace("\n", " | ");
  }
}
