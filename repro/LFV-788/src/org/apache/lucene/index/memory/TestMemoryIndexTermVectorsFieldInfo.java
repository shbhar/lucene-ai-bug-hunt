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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A MemoryIndex reader returns term vectors for every field with terms, but its FieldInfos take
 * storeTermVector from the field type. A document with a term-vector field (body) and a plain text
 * field (title) copied into an index with IndexWriter#addIndexes(SlowCodecReaderWrapper.wrap(r))
 * then gives a segment that CheckIndex reports as broken: "docID=0 has term vectors for
 * field=title but FieldInfo has storeTermVector=false".
 */
public class TestMemoryIndexTermVectorsFieldInfo extends LuceneTestCase {

  private static final Analyzer ANALYZER = new StandardAnalyzer();

  private static Document doc() {
    FieldType withVectors = new FieldType(TextField.TYPE_NOT_STORED);
    withVectors.setStoreTermVectors(true);
    withVectors.freeze();
    Document doc = new Document();
    doc.add(new Field("body", "the quick brown fox", withVectors));
    doc.add(new TextField("title", "fox tales", Field.Store.NO));
    return doc;
  }

  private static LeafReader memoryIndexReader() {
    MemoryIndex mi = MemoryIndex.fromDocument(doc(), ANALYZER);
    return mi.createSearcher().getIndexReader().leaves().get(0).reader();
  }

  private static IndexWriter writer(Directory dir) throws IOException {
    return new IndexWriter(dir, new IndexWriterConfig(ANALYZER));
  }

  private static void assertCheckIndexClean(Directory dir) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (CheckIndex checker = new CheckIndex(dir)) {
      // level 2 (CheckIndex -level 2): the structural checks; level 1 only verifies checksums
      checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
      checker.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
      CheckIndex.Status status = checker.checkIndex();
      assertTrue("CheckIndex is not clean:\n" + bos.toString(StandardCharsets.UTF_8), status.clean);
    }
  }

  /** Reference: the same document indexed by IndexWriter passes CheckIndex. */
  public void testIndexWriterReference() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = writer(dir)) {
        w.addDocument(doc());
      }
      assertCheckIndexClean(dir);
    }
  }

  /** Reader side: every field returned by termVectors() must have FieldInfo#hasTermVectors(). */
  public void testTermVectorFieldsAgreeWithFieldInfos() throws IOException {
    LeafReader reader = memoryIndexReader();
    Fields vectors = reader.termVectors().get(0);
    for (String field : vectors) {
      assertTrue(
          "termVectors() returns field " + field + " but its FieldInfo has storeTermVector=false",
          reader.getFieldInfos().fieldInfo(field).hasTermVectors());
    }
  }

  /** Copy into an empty index. */
  public void testAddIndexesGivesCleanIndex() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = writer(dir)) {
        w.addIndexes(SlowCodecReaderWrapper.wrap(memoryIndexReader()));
      }
      assertCheckIndexClean(dir);
    }
  }

  /**
   * Copy into an index that already holds the same document: addIndexes may reject the reader's
   * schema, but if it accepts it the index must stay clean. On main it accepts it (same schema) and
   * the bad term vectors survive the merge with the native document.
   */
  public void testAddIndexesIntoIndexWithTheSameDocumentGivesCleanIndex() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = writer(dir)) {
        w.addDocument(doc());
        w.commit();
        try {
          w.addIndexes(SlowCodecReaderWrapper.wrap(memoryIndexReader()));
        } catch (IllegalArgumentException schemaMismatch) {
          return; // a loud rejection is acceptable; a silently broken segment is not
        }
        w.forceMerge(1);
      }
      assertCheckIndexClean(dir);
    }
  }
}
