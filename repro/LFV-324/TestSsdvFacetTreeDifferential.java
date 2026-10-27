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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

/**
 * Randomized differential check of SortedSetDocValues faceting against a brute-force reference
 * computed from the live documents. Dimension names and labels are drawn from small alphabets in
 * which names and labels continue each other with characters below, at and above U+001F, over
 * several segments and with deletions. It compares, for every dimension and every indexed path,
 * the children and their counts (getTopChildren), the path value where it is defined, getAllDims,
 * and getTopDims (no null entries, same dims and values as getAllDims).
 */
public class TestSsdvFacetTreeDifferential extends LuceneTestCase {

  // hierarchical dims (multi-valued), flat multi-valued + requireDimCount dims, flat single-valued
  static final String[] HIER_DIMS = {"d", "d\t", "d\t\t", "d\u001E", "e"};
  static final String[] FLAT_COUNT_DIMS = {"t", "t\t", "t\n"};
  static final String[] FLAT_DIMS = {"f", "f\t"};
  static final String[] LABELS = {
    "a", "a\t", "a\t\t", "a\n", "a\u001E", "a\u001F", "a\u001Fb", "a b", "a!", "b"
  };

  public void testRandomIndexes() throws IOException {
    Random random = random();
    int indexes = atLeast(300);
    for (int iter = 0; iter < indexes; iter++) {
      checkOneIndex(random, iter);
    }
  }

  private static FacetsConfig config() {
    FacetsConfig config = new FacetsConfig();
    for (String dim : HIER_DIMS) {
      config.setHierarchical(dim, true);
      config.setMultiValued(dim, true);
    }
    for (String dim : FLAT_COUNT_DIMS) {
      config.setMultiValued(dim, true);
      config.setRequireDimCount(dim, true);
    }
    return config;
  }

  private void checkOneIndex(Random random, int iter) throws IOException {
    FacetsConfig config = config();
    int numDocs = 1 + random.nextInt(10);
    // per doc: dim -> set of paths (each path = label components)
    List<Map<String, Set<List<String>>>> docs = new ArrayList<>();
    boolean[] deleted = new boolean[numDocs];
    try (Directory dir = newDirectory()) {
      // keep fully deleted segments, so every dim stays in the reader (their terms still shape the
      // facet state) and a never-seen hierarchical dim is not queried (a separate known issue)
      IndexWriterConfig iwc =
          newIndexWriterConfig()
              .setMergePolicy(
                  new FilterMergePolicy(NoMergePolicy.INSTANCE) {
                    @Override
                    public boolean keepFullyDeletedSegment(
                        IOSupplier<CodecReader> readerIOSupplier) {
                      return true;
                    }
                  });
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < numDocs; i++) {
          Map<String, Set<List<String>>> paths = new HashMap<>();
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          int numFields = 1 + random.nextInt(4);
          for (int f = 0; f < numFields; f++) {
            int kind = random.nextInt(3);
            String dim;
            List<String> path = new ArrayList<>();
            if (kind == 0) {
              dim = HIER_DIMS[random.nextInt(HIER_DIMS.length)];
              int depth = 1 + random.nextInt(3);
              for (int k = 0; k < depth; k++) {
                path.add(LABELS[random.nextInt(LABELS.length)]);
              }
            } else if (kind == 1) {
              dim = FLAT_COUNT_DIMS[random.nextInt(FLAT_COUNT_DIMS.length)];
              path.add(LABELS[random.nextInt(LABELS.length)]);
            } else {
              dim = FLAT_DIMS[random.nextInt(FLAT_DIMS.length)];
              if (paths.containsKey(dim)) {
                continue; // single-valued
              }
              path.add(LABELS[random.nextInt(LABELS.length)]);
            }
            if (paths.computeIfAbsent(dim, k -> new HashSet<>()).add(path)) {
              doc.add(new SortedSetDocValuesFacetField(dim, path.toArray(new String[0])));
            }
          }
          docs.add(paths);
          w.addDocument(config.build(doc));
          if (random.nextInt(3) == 0) {
            w.commit(); // new segment
          }
        }
        for (int i = 0; i < numDocs; i++) {
          if (random.nextInt(5) == 0) {
            deleted[i] = true;
            w.deleteDocuments(new Term("id", Integer.toString(i)));
          }
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(r, config);
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
        String ctx = "iter=" + iter + " docs=" + describe(docs, deleted);
        Map<String, Integer> expectedDims = new TreeMap<>();
        Set<String> allDims = new HashSet<>();
        for (Map<String, Set<List<String>>> d : docs) {
          allDims.addAll(d.keySet());
        }
        for (String dim : allDims) {
          boolean hier = Arrays.asList(HIER_DIMS).contains(dim);
          // every indexed path prefix of this dim (from all docs, deleted or not)
          Set<List<String>> prefixes = new HashSet<>();
          prefixes.add(List.of());
          for (Map<String, Set<List<String>>> d : docs) {
            for (List<String> p : d.getOrDefault(dim, Set.of())) {
              for (int k = 1; k <= p.size(); k++) {
                prefixes.add(p.subList(0, k));
              }
            }
          }
          for (List<String> prefix : prefixes) {
            if (hier == false && prefix.isEmpty() == false) {
              continue;
            }
            // brute force: child label -> number of live docs with a path under prefix + label
            Map<String, Integer> expected = new TreeMap<>();
            int pathDocs = 0;
            for (int i = 0; i < numDocs; i++) {
              if (deleted[i]) {
                continue;
              }
              Set<String> childLabels = new HashSet<>();
              boolean hasPath = false;
              for (List<String> p : docs.get(i).getOrDefault(dim, Set.of())) {
                if (p.size() >= prefix.size() && p.subList(0, prefix.size()).equals(prefix)) {
                  hasPath = true;
                  if (p.size() > prefix.size()) {
                    childLabels.add(p.get(prefix.size()));
                  }
                }
              }
              if (hasPath) {
                pathDocs++;
              }
              for (String l : childLabels) {
                expected.merge(l, 1, Integer::sum);
              }
            }
            String where = ctx + " dim=" + esc(dim) + " path=" + esc(prefix.toString());
            FacetResult actual;
            try {
              actual = facets.getTopChildren(1000, dim, prefix.toArray(new String[0]));
            } catch (RuntimeException e) {
              throw new AssertionError("getTopChildren threw " + e + " " + where, e);
            }
            Map<String, Integer> actualMap = new TreeMap<>();
            if (actual != null) {
              for (LabelAndValue lv : actual.labelValues) {
                actualMap.put(lv.label, lv.value.intValue());
              }
            }
            assertEquals("children " + where, esc(expected.toString()), esc(actualMap.toString()));
            if (actual != null) {
              int expectedValue;
              if (hier || Arrays.asList(FLAT_COUNT_DIMS).contains(dim)) {
                expectedValue = pathDocs;
              } else {
                expectedValue = pathDocs; // single-valued flat: sum of children = docs with dim
              }
              assertEquals("value " + where, expectedValue, actual.value.intValue());
            }
            if (prefix.isEmpty() && expected.isEmpty() == false) {
              expectedDims.put(dim, pathDocs);
            }
          }
        }
        Map<String, Integer> actualDims = new TreeMap<>();
        for (FacetResult fr : facets.getAllDims(1000)) {
          actualDims.put(fr.dim, fr.value.intValue());
        }
        assertEquals("getAllDims " + ctx, esc(expectedDims.toString()), esc(actualDims.toString()));
        Map<String, Integer> topDims = new TreeMap<>();
        for (FacetResult fr : facets.getTopDims(1000, 1000)) {
          assertNotNull("getTopDims null entry " + ctx, fr);
          topDims.put(fr.dim, fr.value.intValue());
        }
        assertEquals("getTopDims " + ctx, esc(expectedDims.toString()), esc(topDims.toString()));
      }
    }
  }

  private static String describe(List<Map<String, Set<List<String>>>> docs, boolean[] deleted) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < docs.size(); i++) {
      sb.append(deleted[i] ? "[deleted " : "[").append(esc(new TreeMap<>(docs.get(i)).toString()));
      sb.append(']');
    }
    return sb.toString();
  }

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c < 0x20) {
        sb.append(String.format(java.util.Locale.ROOT, "<%02X>", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
