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
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** {@link DatasetSplitter} over an index in which some documents have no class value. */
public class TestDatasetSplitterMissingClassValues extends LuceneTestCase {

  private static final String CLASS_FIELD = "cls";

  /** A document with an id, some text and, unless {@code cls} is null, a class value. */
  private static Document doc(String id, String cls) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new TextField("text", "some text for " + id, Field.Store.YES));
    if (cls != null) {
      doc.add(new StringField(CLASS_FIELD, cls, Field.Store.YES));
      doc.add(new SortedDocValuesField(CLASS_FIELD, new BytesRef(cls)));
    }
    return doc;
  }

  /** The ids of the documents in all three outputs, sorted. */
  private static List<String> idsInOutputs(Directory... outputs) throws Exception {
    List<String> ids = new ArrayList<>();
    for (Directory dir : outputs) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        for (int i = 0; i < reader.maxDoc(); i++) {
          ids.add(reader.storedFields().document(i).get("id"));
        }
      }
    }
    Collections.sort(ids);
    return ids;
  }

  /** One segment: d0 (no class value), d1 (class a), d2 (class b). */
  public void testUnlabelledDocumentDoesNotDropALabelledClass() throws Exception {
    try (Analyzer analyzer = new MockAnalyzer(random());
        Directory source = newDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(source, new IndexWriterConfig(analyzer))) {
        writer.addDocument(doc("d0", null));
        writer.addDocument(doc("d1", "a"));
        writer.addDocument(doc("d2", "b"));
        writer.forceMerge(1);
      }
      DirectoryReader reader = DirectoryReader.open(source);
      assertEquals(1, reader.leaves().size());
      try {
        new DatasetSplitter(0.1, 0.1).split(reader, train, test, cv, analyzer, false, CLASS_FIELD);
      } finally {
        reader.close(); // split() closes it too; a second close is a no-op
      }
      List<String> ids = idsInOutputs(train, test, cv);
      assertEquals(
          "d1 (class a) in exactly one output: " + ids, 1, Collections.frequency(ids, "d1"));
      assertEquals(
          "d2 (class b) in exactly one output: " + ids, 1, Collections.frequency(ids, "d2"));
    }
  }

  /** Two segments: [d0 (class a)] and [d1 (no class field at all)]. */
  public void testSegmentWithoutClassField() throws Exception {
    try (Analyzer analyzer = new MockAnalyzer(random());
        Directory source = newDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(analyzer).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter writer = new IndexWriter(source, iwc)) {
        writer.addDocument(doc("d0", "a"));
        writer.commit();
        writer.addDocument(doc("d1", null));
        writer.commit();
      }
      DirectoryReader reader = DirectoryReader.open(source);
      assertEquals(2, reader.leaves().size());
      Throwable thrown = null;
      try {
        new DatasetSplitter(0.1, 0.1).split(reader, train, test, cv, analyzer, false, CLASS_FIELD);
      } catch (Throwable t) {
        thrown = t;
      } finally {
        reader.close();
      }
      // split() must close its three writers whatever happens: no output may stay locked
      List<String> locked = new ArrayList<>();
      for (Directory dir : new Directory[] {train, test, cv}) {
        try (IndexWriter probe = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
          assertNotNull(probe);
        } catch (LockObtainFailedException _) {
          locked.add(dir == train ? "training" : dir == test ? "test" : "cross-validation");
        }
      }
      if (thrown != null) {
        throw new AssertionError("split threw " + thrown + "; locked outputs: " + locked, thrown);
      }
      assertEquals("locked outputs", List.of(), locked);
      List<String> ids = idsInOutputs(train, test, cv);
      assertTrue("labelled document d0 in the outputs: " + ids, ids.contains("d0"));
    }
  }
}
