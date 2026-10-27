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
package org.apache.lucene.classification.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * DatasetSplitter over an index where some documents have no value in the class field.
 *
 * <p>The class field is indexed as in TestDataSplitter: a stored text field plus a
 * SortedDocValuesField with the same name.
 */
public class TestDatasetSplitterMissingClass extends LuceneTestCase {

  private static final String CLS = "cls";

  private static Document doc(String id, String cls) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new TextField("body", "some text for " + id, Field.Store.YES));
    if (cls != null) {
      d.add(new Field(CLS, cls, TextField.TYPE_STORED));
      d.add(new SortedDocValuesField(CLS, new BytesRef(cls)));
    }
    return d;
  }

  /** Builds an index; each inner array is flushed as its own segment (no merging). */
  private static Directory buildIndex(Analyzer a, String[][]... segments) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(a).setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String[][] seg : segments) {
        for (String[] idAndClass : seg) {
          w.addDocument(doc(idAndClass[0], idAndClass[1]));
        }
        w.commit();
      }
    }
    return dir;
  }

  private static List<String> ids(Directory dir) throws Exception {
    List<String> out = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (int i = 0; i < r.maxDoc(); i++) {
        out.add(r.storedFields().document(i).get("id"));
      }
    }
    return out;
  }

  /** Runs split and returns every id found in the three outputs (with multiplicity), sorted. */
  private static List<String> splitAndCollect(Directory src, int expectedLeaves) throws Exception {
    Analyzer a = new MockAnalyzer(random());
    Directory train = newDirectory();
    Directory test = newDirectory();
    Directory cv = newDirectory();
    try {
      DirectoryReader reader = DirectoryReader.open(src);
      assertEquals("index layout", expectedLeaves, reader.leaves().size());
      // split() closes the reader it is given
      new DatasetSplitter(0.2, 0.2).split(reader, train, test, cv, a, false, CLS);
      List<String> all = new ArrayList<>();
      all.addAll(ids(train));
      all.addAll(ids(test));
      all.addAll(ids(cv));
      Collections.sort(all);
      return all;
    } finally {
      train.close();
      test.close();
      cv.close();
      a.close();
    }
  }

  /** Control: every document labelled, one segment: every document is in exactly one output. */
  public void testControlAllLabelled() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src =
        buildIndex(a, new String[][] {{"d0", "a"}, {"d1", "a"}, {"d2", "b"}, {"d3", "c"}})) {
      assertEquals(List.of("d0", "d1", "d2", "d3"), splitAndCollect(src, 1));
    }
    a.close();
  }

  /**
   * One segment: u0 (no class), d1 (class a), d2 (class b). Labelled documents must not be lost.
   */
  public void testUnlabelledFirstLosesLabelledDoc() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src = buildIndex(a, new String[][] {{"u0", null}, {"d1", "a"}, {"d2", "b"}})) {
      List<String> got = splitAndCollect(src, 1);
      assertTrue(
          "labelled documents d1 (class a) and d2 (class b) must both be in the outputs; got " + got,
          got.contains("d1") && got.contains("d2"));
      assertEquals("every document exactly once", List.of("d1", "d2", "u0"), got);
    }
    a.close();
  }

  /**
   * Same documents, unlabelled one last: shows the result depends only on index order. Under the
   * "every live document" reading u0 is missing; labelled docs survive.
   */
  public void testUnlabelledLastLosesUnlabelledDoc() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    try (Directory src = buildIndex(a, new String[][] {{"d1", "a"}, {"d2", "b"}, {"u0", null}})) {
      List<String> got = splitAndCollect(src, 1);
      assertTrue("labelled documents survive here; got " + got, got.contains("d1") && got.contains("d2"));
      assertEquals("every document exactly once", List.of("d1", "d2", "u0"), got);
    }
    a.close();
  }

  /**
   * Two segments, the second has no document with the class field. split must not throw an
   * undocumented exception, and must not leave the output directories locked.
   */
  public void testSegmentWithoutClassField() throws Exception {
    Analyzer a = new MockAnalyzer(random());
    // plain directories for the outputs: a leaked IndexWriter would make MockDirectoryWrapper
    // fail on close, which would hide the assertion below
    Directory train = new ByteBuffersDirectory();
    Directory test = new ByteBuffersDirectory();
    Directory cv = new ByteBuffersDirectory();
    try (Directory src =
        buildIndex(a, new String[][] {{"d0", "a"}, {"d1", "b"}}, new String[][] {{"u2", null}})) {
      DirectoryReader reader = DirectoryReader.open(src);
      assertEquals(2, reader.leaves().size());
      Throwable thrown = null;
      try {
        new DatasetSplitter(0.2, 0.2).split(reader, train, test, cv, a, false, CLS);
      } catch (Throwable t) {
        thrown = t;
      } finally {
        reader.close();
      }
      String lockState;
      try (IndexWriter probe = new IndexWriter(train, new IndexWriterConfig(a))) {
        lockState = "training directory unlocked";
      } catch (org.apache.lucene.store.LockObtainFailedException e) {
        lockState = "training directory still locked: " + e;
      }
      if (thrown != null) {
        throw new AssertionError("split threw " + thrown + " ; " + lockState, thrown);
      }
      assertEquals("training directory unlocked", lockState);
      List<String> all = new ArrayList<>();
      all.addAll(ids(train));
      all.addAll(ids(test));
      all.addAll(ids(cv));
      Collections.sort(all);
      assertTrue("labelled documents must be in the outputs; got " + all, all.containsAll(List.of("d0", "d1")));
    } finally {
      train.close();
      test.close();
      cv.close();
      a.close();
    }
  }
}
