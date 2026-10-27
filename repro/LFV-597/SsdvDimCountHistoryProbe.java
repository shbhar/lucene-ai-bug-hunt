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
package probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Version-neutral history probe (runs unchanged on 9.0.0 .. main; public API only).
 *
 * <p>Differential oracle inside one Lucene version: a flat, multiValued SortedSetDocValues
 * dimension whose name contains U+001F or U+001E must give exactly the same facet results as the
 * same dimension named "color" (same docs {x, y}, {x}, {z}), apart from the dimension name. The
 * oracle is relative on purpose: before LUCENE-9952 (9.0.0) the dimension value of a multiValued
 * SSDV dimension was the documented sum of label counts (4), so an absolute "value=3" assertion
 * does not apply there.
 *
 * <p>The only reflection is the choice between two PUBLIC constructors of
 * DefaultSortedSetDocValuesReaderState: (IndexReader, FacetsConfig) where it exists (9.1.0+),
 * otherwise (IndexReader) (9.0.0). No private state is touched.
 *
 * <p>Exit 0: every reserved-name case matches the plain-name result (not affected). Exit 1: at
 * least one requireDimCount=true case differs or throws (affected). Exit 2: a requireDimCount=false
 * control differs (probe or a different defect, not this one).
 */
public class SsdvDimCountHistoryProbe {

  static String show(String s) {
    return s.replace("\u001F", "<1F>").replace("\u001E", "<1E>");
  }

  static SortedSetDocValuesReaderState newState(IndexReader reader, FacetsConfig config)
      throws Throwable {
    try {
      return newState0(reader, config);
    } catch (InvocationTargetException e) {
      throw e.getCause(); // report what the Lucene constructor threw, not the reflection wrapper
    }
  }

  static SortedSetDocValuesReaderState newState0(IndexReader reader, FacetsConfig config)
      throws Exception {
    try {
      Constructor<DefaultSortedSetDocValuesReaderState> c =
          DefaultSortedSetDocValuesReaderState.class.getConstructor(
              IndexReader.class, FacetsConfig.class);
      return c.newInstance(reader, config);
    } catch (NoSuchMethodException e) {
      Constructor<DefaultSortedSetDocValuesReaderState> c =
          DefaultSortedSetDocValuesReaderState.class.getConstructor(IndexReader.class);
      return c.newInstance(reader);
    }
  }

  static String describe(FacetResult r, String dim) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(r.dim.equals(dim) ? "<D>" : show(r.dim));
    sb.append(" value=").append(r.value).append(" childCount=").append(r.childCount).append(" [");
    for (int i = 0; i < r.labelValues.length; i++) {
      LabelAndValue lv = r.labelValues[i];
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(show(lv.label)).append('=').append(lv.value);
    }
    return sb.append(']').toString();
  }

  /** Index docs {x, y}, {x}, {z} under dim; return a dim-name-normalised description. */
  static String run(String dim, boolean requireDimCount) {
    try {
      FacetsConfig config = new FacetsConfig();
      config.setMultiValued(dim, true);
      config.setRequireDimCount(dim, requireDimCount);
      try (Directory dir = new ByteBuffersDirectory()) {
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
          SortedSetDocValuesReaderState state = newState(reader, config);
          Facets facets = new SortedSetDocValuesFacetCounts(state);
          String top = describe(facets.getTopChildren(10, dim), dim);
          List<String> dims = new ArrayList<>();
          for (FacetResult fr : facets.getAllDims(10)) {
            dims.add(describe(fr, dim));
          }
          return "top={" + top + "} allDims=" + dims;
        }
      }
    } catch (Throwable t) {
      StackTraceElement[] st = t.getStackTrace();
      String where = st.length > 0 ? " at " + st[0] : "";
      return "THREW " + t.getClass().getName() + ": " + show(String.valueOf(t.getMessage())) + where;
    }
  }

  public static void main(String[] args) {
    System.out.println(
        "lucene=" + org.apache.lucene.util.Version.LATEST + " assertions=" + assertionsOn());
    String[] reserved = {"color\u001Fcode", "color\u001Ecode", "color\u001E"};
    int bug = 0;
    int controlDiff = 0;
    for (boolean rdc : new boolean[] {true, false}) {
      String expected = run("color", rdc);
      System.out.println("requireDimCount=" + rdc + " dim=color (reference): " + expected);
      for (String dim : reserved) {
        String got = run(dim, rdc);
        boolean same = got.equals(expected);
        System.out.println(
            "requireDimCount=" + rdc + " dim=" + show(dim) + ": " + (same ? "SAME" : "DIFF ") + (same ? "" : got));
        if (!same) {
          if (rdc) {
            bug++;
          } else {
            controlDiff++;
          }
        }
      }
    }
    System.out.println(
        "SUMMARY affected_cases=" + bug + "/3 control_diffs=" + controlDiff + "/3 -> "
            + (controlDiff > 0 ? "CONTROL-MISMATCH" : bug > 0 ? "AFFECTED" : "NOT-AFFECTED"));
    System.exit(controlDiff > 0 ? 2 : bug > 0 ? 1 : 0);
  }

  @SuppressWarnings("AssertWithSideEffects")
  static boolean assertionsOn() {
    boolean on = false;
    assert on = true;
    return on;
  }
}
