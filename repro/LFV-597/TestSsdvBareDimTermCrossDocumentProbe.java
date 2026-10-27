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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Security probe: what can the author of ONE document do through the unescaped bare dimension
 * term (FacetsConfig.java:494), in an application that lets documents bring their own facet
 * dimension names (getDefaultDimConfig returns multiValued + requireDimCount, as its javadoc
 * suggests for dimensions "not known beforehand") next to an application-owned dimension
 * "status"?
 *
 * <p>Public API only, default codec, one commit without merges. Each test asserts the CORRECT
 * behaviour, so a failing test shows the effect on the stock build. Controls are named
 * testControl*.
 */
public class TestSsdvBareDimTermCrossDocumentProbe extends LuceneTestCase {

  private static FacetsConfig appConfig() {
    FacetsConfig.DimConfig userDims = new FacetsConfig.DimConfig();
    userDims.multiValued = true;
    userDims.requireDimCount = true;
    FacetsConfig config =
        new FacetsConfig() {
          @Override
          protected DimConfig getDefaultDimConfig() {
            return userDims;
          }
        };
    // application-owned dimension, configured explicitly: flat, single-valued
    config.setHierarchical("status", false);
    return config;
  }

  private static Document doc(String id, boolean visible, String[]... dimLabel) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new StringField("visibility", visible ? "public" : "private", Field.Store.NO));
    for (String[] dl : dimLabel) {
      doc.add(new SortedSetDocValuesFacetField(dl[0], dl[1]));
    }
    return doc;
  }

  private static String[] dl(String dim, String label) {
    return new String[] {dim, label};
  }

  /** Two visible documents, plus the author's document. */
  private static void index(Directory dir, FacetsConfig config, Document authorDoc)
      throws Exception {
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(
          config.build(doc("p1", true, dl("status", "approved"), dl("color", "red"))));
      w.addDocument(
          config.build(doc("p2", true, dl("status", "pending"), dl("color", "blue"))));
      w.addDocument(config.build(authorDoc));
      w.commit();
    }
  }

  private static String counts(FacetResult r) {
    if (r == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder("value=" + r.value);
    for (LabelAndValue lv : r.labelValues) {
      sb.append(' ').append(lv.label).append('=').append(lv.value);
    }
    return sb.toString();
  }

  private static final Query PUBLIC = new TermQuery(new Term("visibility", "public"));

  /** What a user who may only see public documents gets. */
  private static String publicView(DirectoryReader reader, FacetsConfig config) throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    FacetsCollector fc = searcher.search(PUBLIC, new FacetsCollectorManager());
    SortedSetDocValuesReaderState state;
    try {
      state = new DefaultSortedSetDocValuesReaderState(reader, config);
    } catch (RuntimeException | AssertionError e) {
      return "facet state construction threw " + e;
    }
    Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
    List<String> dims = new ArrayList<>();
    for (FacetResult r : facets.getAllDims(10)) {
      dims.add(r.dim);
    }
    dims.sort(null);
    return "status: "
        + counts(facets.getTopChildren(10, "status"))
        + " | color: "
        + counts(facets.getTopChildren(10, "color"))
        + " | dims: "
        + dims;
  }

  private static final String CORRECT_PUBLIC_VIEW =
      "status: value=2 approved=1 pending=1 | color: value=2 blue=1 red=1 | dims: [color, status]";

  /** A hidden document whose dimension name holds two U+001F. */
  public void testHiddenDocBreaksFacetStateForEveryone() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", false, dl("a\u001Fb\u001Fc", "x")));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(
            "the public view must not depend on a private document",
            CORRECT_PUBLIC_VIEW,
            publicView(reader, config));
      }
    }
  }

  /** The same document deleted, before any merge. */
  public void testDeletedDocStillBreaksFacetState() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", false, dl("a\u001Fb\u001Fc", "x")));
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.deleteDocuments(new Term("id", "author"));
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.numDocs());
        assertEquals(3, reader.maxDoc());
        assertEquals(
            "a deleted document must not change the public view",
            CORRECT_PUBLIC_VIEW,
            publicView(reader, config));
      }
    }
  }

  /** Control: after the deleted document is merged away, faceting works again. */
  public void testControlMergeAwayDeletedDocRestoresFacetState() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", false, dl("a\u001Fb\u001Fc", "x")));
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.deleteDocuments(new Term("id", "author"));
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.numDocs());
        assertEquals(2, reader.maxDoc());
        assertEquals(CORRECT_PUBLIC_VIEW, publicView(reader, config));
      }
    }
  }

  /** Control: a hidden document with a plain dimension name changes nothing (probe not vacuous). */
  public void testControlHiddenDocPlainDimName() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", false, dl("a b c", "x")));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(CORRECT_PUBLIC_VIEW, publicView(reader, config));
      }
    }
  }

  /** A hidden document whose dimension name ends in U+001E (fails only with -ea). */
  public void testHiddenDocEndingInRecordSeparator() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", false, dl("note\u001E", "x")));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(CORRECT_PUBLIC_VIEW, publicView(reader, config));
      }
    }
  }

  /**
   * A visible document that has no "status" value but a user dimension named
   * "status<U+001F>approved": it must not be counted under status=approved.
   */
  public void testVisibleDocCountedUnderApplicationDimension() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", true, dl("status\u001Fapproved", "x")));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        DrillDownQuery ddq = new DrillDownQuery(config, PUBLIC);
        ddq.add("status", "approved");
        // drill-down uses the escaped drill-down terms and stays correct:
        assertEquals("drill-down status/approved", 1, searcher.count(ddq));
        String view = publicView(reader, config);
        assertTrue(
            "status counts must not include the author's document: " + view,
            view.startsWith("status: value=2 approved=1 pending=1 |"));
      }
    }
  }

  /** Control: the same document hidden from the user does not reach that user's counts. */
  public void testControlHiddenDocNotCountedForOthers() throws Exception {
    FacetsConfig config = appConfig();
    try (Directory dir = newDirectory()) {
      index(dir, config, doc("author", false, dl("status\u001Fapproved", "x")));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(CORRECT_PUBLIC_VIEW, publicView(reader, config));
      }
    }
  }
}
