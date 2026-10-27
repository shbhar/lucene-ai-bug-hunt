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
package org.apache.lucene.index.memory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.misc.document.LazyDocument;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * StoredFieldVisitor#needsField: "Before a field is processed, this method is invoked so that
 * subclasses can return a Status representing whether they need that particular field". An index
 * written by IndexWriter asks once per stored value and never about a field without a stored value;
 * MemoryIndex asks about every field name it has, once, and then delivers all of its stored values.
 * LazyDocument#getField depends on the per-value calls ("must be called once for each value of the
 * field name specified in sequence that the values exist").
 */
public class TestMemoryIndexStoredFieldVisitorCalls extends LuceneTestCase {

  private static Document doc() {
    Document d = new Document();
    d.add(new TextField("body", "quick brown fox", Field.Store.NO)); // indexed, not stored
    d.add(new StoredField("tag", "t1"));
    d.add(new StoredField("tag", "t2"));
    return d;
  }

  /** The lazy-loading visitor of the LazyDocument tests: one lazy field per offered field. */
  private static List<String> lazyLoad(IndexReader reader) throws IOException {
    LazyDocument lazyDoc = new LazyDocument(reader, 0);
    List<IndexableField> lazyFields = new ArrayList<>();
    reader
        .storedFields()
        .document(
            0,
            new StoredFieldVisitor() {
              @Override
              public Status needsField(FieldInfo fieldInfo) {
                lazyFields.add(lazyDoc.getField(fieldInfo));
                return Status.NO;
              }
            });
    List<String> names = new ArrayList<>();
    for (IndexableField f : lazyFields) {
      names.add(f.name());
    }
    return names;
  }

  /** Same visitor, then reads every lazy value (what an application using LazyDocument sees). */
  private static List<String> lazyValues(IndexReader reader) throws IOException {
    LazyDocument lazyDoc = new LazyDocument(reader, 0);
    List<IndexableField> lazyFields = new ArrayList<>();
    reader
        .storedFields()
        .document(
            0,
            new StoredFieldVisitor() {
              @Override
              public Status needsField(FieldInfo fieldInfo) {
                lazyFields.add(lazyDoc.getField(fieldInfo));
                return Status.NO;
              }
            });
    List<String> values = new ArrayList<>();
    for (IndexableField f : lazyFields) {
      try {
        values.add(f.name() + "=" + f.stringValue());
      } catch (RuntimeException | AssertionError e) {
        values.add(f.name() + " -> " + e.getClass().getSimpleName());
      }
    }
    return values;
  }

  public void testLazyDocumentValues() throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(List.of("tag=t1", "tag=t2"), lazyValues(reader));
      }
    }
    MemoryIndex mi = new MemoryIndex();
    for (IndexableField f : doc()) {
      mi.addField(f, analyzer);
    }
    assertEquals(List.of("tag=t1", "tag=t2"), lazyValues(mi.createSearcher().getIndexReader()));
  }

  public void testNeedsFieldOncePerStoredValue() throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // control: the IndexWriter-backed reader offers each stored value, and only those
        assertEquals(List.of("tag", "tag"), lazyLoad(reader));
      }
    }

    MemoryIndex mi = new MemoryIndex();
    for (IndexableField f : doc()) {
      mi.addField(f, analyzer);
    }
    IndexReader memoryReader = mi.createSearcher().getIndexReader();
    assertEquals("t1", memoryReader.storedFields().document(0).getValues("tag")[0]);
    assertEquals(2, memoryReader.storedFields().document(0).getValues("tag").length);
    assertEquals(List.of("tag", "tag"), lazyLoad(memoryReader));
  }
}
