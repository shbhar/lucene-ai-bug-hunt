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
package mixed;

import java.nio.file.Paths;
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
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * What a fixed build sees in an index whose older segments were written by an unfixed build.
 *
 * <p>Usage: {@code write DIR} (creates DIR with docs {x, y}, {x}, {z}), {@code append DIR} (adds
 * the same three docs as a new segment), {@code merge DIR} (forceMerge(1)), {@code print DIR}.
 * Every mode prints the facet result afterwards. Run write with one build and append/merge with
 * another.
 */
public class SsdvMixedSegmentsProbe {

  static final String DIM = "color\u001Fcode";

  static FacetsConfig config() {
    FacetsConfig config = new FacetsConfig();
    config.setMultiValued(DIM, true);
    config.setRequireDimCount(DIM, true);
    return config;
  }

  static String show(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(c < 0x20 ? String.format(Locale.ROOT, "\\u%04X", (int) c) : String.valueOf(c));
    }
    return sb.toString();
  }

  static void addDocs(IndexWriter w, FacetsConfig config) throws Exception {
    for (String[] labels : new String[][] {{"x", "y"}, {"x"}, {"z"}}) {
      Document doc = new Document();
      for (String label : labels) {
        doc.add(new SortedSetDocValuesFacetField(DIM, label));
      }
      w.addDocument(config.build(doc));
    }
  }

  public static void main(String[] args) throws Exception {
    String mode = args[0];
    FacetsConfig config = config();
    try (Directory dir = FSDirectory.open(Paths.get(args[1]))) {
      if (mode.equals("write") || mode.equals("append") || mode.equals("merge")) {
        IndexWriterConfig iwc =
            new IndexWriterConfig()
                .setOpenMode(
                    mode.equals("write")
                        ? IndexWriterConfig.OpenMode.CREATE
                        : IndexWriterConfig.OpenMode.APPEND);
        if (mode.equals("merge") == false) {
          iwc.setMergePolicy(NoMergePolicy.INSTANCE); // keep the old and new segments apart
        }
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          if (mode.equals("merge")) {
            w.forceMerge(1);
          } else {
            addDocs(w, config);
          }
          w.commit();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets =
            new SortedSetDocValuesFacetCounts(
                new DefaultSortedSetDocValuesReaderState(reader, config), fc);
        FacetResult r = facets.getTopChildren(10, DIM);
        StringBuilder sb =
            new StringBuilder(
                mode + ": segments=" + reader.leaves().size() + " docs=" + reader.numDocs());
        sb.append(" | ").append(show(DIM)).append(": value=").append(r.value);
        for (LabelAndValue lv : r.labelValues) {
          sb.append(' ').append(lv.label).append('=').append(lv.value);
        }
        List<String> dims = new ArrayList<>();
        for (FacetResult d : facets.getAllDims(10)) {
          StringBuilder ds = new StringBuilder(show(d.dim)).append('{');
          for (LabelAndValue lv : d.labelValues) {
            ds.append(show(lv.label)).append('=').append(lv.value).append(' ');
          }
          dims.add(ds.toString().trim() + "}");
        }
        sb.append(" | getAllDims=").append(dims);
        System.out.println(sb);
      }
    }
  }
}
