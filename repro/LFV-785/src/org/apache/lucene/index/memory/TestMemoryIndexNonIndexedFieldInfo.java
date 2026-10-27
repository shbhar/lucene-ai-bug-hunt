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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A MemoryIndex field that indexes no terms (here a NumericDocValuesField; IntPoint,
 * KnnFloatVectorField and StoredField behave the same) is reported with index options
 * DOCS_AND_FREQS_AND_POSITIONS. FieldInfo#getIndexOptions: "Returns IndexOptions for the field,
 * or IndexOptions.NONE if the field is not indexed". Copying such a reader with
 * IndexWriter#addIndexes(SlowCodecReaderWrapper.wrap(reader)) then fails against an index that
 * holds the same document, or makes the target reject the same document afterwards.
 */
public class TestMemoryIndexNonIndexedFieldInfo extends LuceneTestCase {

  private static final Analyzer ANALYZER = new StandardAnalyzer();

  private static Document doc() {
    Document doc = new Document();
    doc.add(new TextField("title", "lucene in action", Field.Store.NO));
    doc.add(new NumericDocValuesField("price", 42));
    return doc;
  }

  private static LeafReader memoryIndexReader() {
    MemoryIndex mi = MemoryIndex.fromDocument(doc(), ANALYZER);
    return mi.createSearcher().getIndexReader().leaves().get(0).reader();
  }

  private static IndexWriter writer(Directory dir) throws IOException {
    return new IndexWriter(dir, new IndexWriterConfig(ANALYZER));
  }

  /** Reference: IndexWriter itself reports the doc-values-only field as not indexed. */
  public void testIndexWriterReference() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = writer(dir)) {
        w.addDocument(doc());
        w.addDocument(doc());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        FieldInfo fi = r.leaves().get(0).reader().getFieldInfos().fieldInfo("price");
        assertEquals(IndexOptions.NONE, fi.getIndexOptions());
        assertFalse(fi.hasNorms());
      }
    }
  }

  /** Reader side: the field has no terms, so it is not indexed. */
  public void testDocValuesOnlyFieldIsNotIndexed() throws IOException {
    LeafReader reader = memoryIndexReader();
    assertNull("no terms for a doc-values-only field", reader.terms("price"));
    FieldInfo fi = reader.getFieldInfos().fieldInfo("price");
    assertEquals(
        "FieldInfo#getIndexOptions: NONE if the field is not indexed",
        IndexOptions.NONE,
        fi.getIndexOptions());
  }

  /** Copy into an index that already holds the same document. */
  public void testAddIndexesIntoIndexWithTheSameDocument() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir)) {
      w.addDocument(doc());
      w.commit();
      // throws IllegalArgumentException: cannot change field "price" from index options=NONE to
      // inconsistent index options=DOCS_AND_FREQS_AND_POSITIONS
      w.addIndexes(SlowCodecReaderWrapper.wrap(memoryIndexReader()));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }

  /** Copy into an empty index, then add the same document the ordinary way. */
  public void testAddSameDocumentAfterAddIndexes() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = writer(dir)) {
        w.addIndexes(SlowCodecReaderWrapper.wrap(memoryIndexReader()));
        w.commit();
      }
      // also in a later writer session: the schema is persisted with the segment
      try (IndexWriter w = writer(dir)) {
        // throws IllegalArgumentException: cannot change field "price" from index
        // options=DOCS_AND_FREQS_AND_POSITIONS to inconsistent index options=NONE
        w.addDocument(doc());
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }
}
