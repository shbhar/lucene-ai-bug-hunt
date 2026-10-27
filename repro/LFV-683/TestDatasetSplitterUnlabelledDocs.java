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

import java.io.IOException;
import java.util.ArrayList;
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
 * DatasetSplitter computes the number of groups to fetch from the class values only. Documents
 * without a class value form one more group, so the last group in index order is dropped from
 * all three outputs; and a segment that has no class field at all makes split throw
 * NullPointerException (not the documented IOException) before its three writers are closed.
 */
public class TestDatasetSplitterUnlabelledDocs extends LuceneTestCase {

  private static Document doc(String id, String cls) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new TextField("text", "text of " + id, Field.Store.YES));
    if (cls != null) {
      d.add(new StringField("cls", cls, Field.Store.YES));
      d.add(new SortedDocValuesField("cls", new BytesRef(cls)));
    }
    return d;
  }

  private static List<String> ids(Directory dir) throws IOException {
    List<String> ids = new ArrayList<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (int i = 0; i < r.maxDoc(); i++) {
        ids.add(r.storedFields().document(i).get("id"));
      }
    }
    return ids;
  }

  /** One segment: d0 (no class), d1 (class a), d2 (class b). */
  public void testUnlabelledDocumentDropsLabelledClass() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory src = newDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("d0", null));
        w.addDocument(doc("d1", "a"));
        w.addDocument(doc("d2", "b"));
        w.forceMerge(1);
      }
      DirectoryReader reader = DirectoryReader.open(src);
      new DatasetSplitter(0.1, 0.1).split(reader, train, test, cv, analyzer, false, "cls");
      reader.close(); // split closes it already; closing twice is a no-op
      List<String> all = new ArrayList<>();
      all.addAll(ids(train));
      all.addAll(ids(test));
      all.addAll(ids(cv));
      assertTrue(
          "labelled document d2 (class b) is missing from all three outputs: " + all,
          all.contains("d2"));
      assertEquals("every document in exactly one output: " + all, 3, all.size());
    }
  }

  /** Two segments: d0 (class a) and d1 (no class); the second segment has no class field. */
  public void testSegmentWithoutClassField() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory src = newDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(src, iwc)) {
        w.addDocument(doc("d0", "a"));
        w.commit();
        w.addDocument(doc("d1", null));
        w.commit();
      }
      DirectoryReader reader = DirectoryReader.open(src);
      assertEquals(2, reader.leaves().size());
      Throwable undocumented = null;
      try {
        new DatasetSplitter(0.1, 0.1).split(reader, train, test, cv, analyzer, false, "cls");
      } catch (IOException documented) {
        // the documented failure type ("if any writing operation fails"); tolerated here
      } catch (RuntimeException e) {
        undocumented = e;
      } finally {
        reader.close();
      }
      // the output directories must not be left locked by writers that split() never closed
      Throwable locked = null;
      try (IndexWriter again = new IndexWriter(train, new IndexWriterConfig(analyzer))) {
        assertNotNull(again);
      } catch (IOException e) {
        locked = e;
      }
      assertTrue(
          "split threw an undocumented exception: "
              + undocumented
              + " / and left the training directory locked: "
              + locked,
          undocumented == null && locked == null);
    }
  }
}
