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
package org.apache.lucene.misc.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.TermVectors;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter.addIndexes(CodecReader...) with a reordering merge (BPReorderingMergePolicy, or any
 * OneMerge.reorder) reads documents through a concatenated view of the input readers. When an input
 * reader with maxDoc() == 0 sits between two non-empty readers, the first document of the next
 * reader is looked up in the empty reader, so its stored fields and term vectors are silently
 * dropped from the new segment. Only public API is used. (Variant without the identity-reorder
 * method, whose OneMerge.reorder signature differs before 9.11.)
 */
public class TestReorderedAddIndexesWithEmptyReader extends LuceneTestCase {

  private static final FieldType BODY_TYPE = new FieldType(TextField.TYPE_STORED);

  static {
    BODY_TYPE.setStoreTermVectors(true);
    BODY_TYPE.freeze();
  }

  private static Directory buildIndex(String prefix, int numDocs) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", prefix + i, Field.Store.YES));
        doc.add(new Field("body", "hello " + prefix + i, BODY_TYPE));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Adds [3 docs, empty reader, 2 docs] and checks that every document kept its stored fields. */
  private void addAndCheck(MergePolicy mergePolicy) throws IOException {
    Directory a = buildIndex("a", 3);
    Directory b = buildIndex("b", 2);
    try (DirectoryReader ra = DirectoryReader.open(a);
        DirectoryReader rb = DirectoryReader.open(b);
        Directory target = newDirectory()) {
      // A legal CodecReader with maxDoc() == 0 (TestParallelReaderEmptyIndex adds the same reader).
      CodecReader empty = SlowCodecReaderWrapper.wrap(new ParallelLeafReader());
      assertEquals(0, empty.maxDoc());
      try (IndexWriter w =
          new IndexWriter(target, new IndexWriterConfig().setMergePolicy(mergePolicy))) {
        w.addIndexes(
            (CodecReader) ra.leaves().get(0).reader(),
            empty,
            (CodecReader) rb.leaves().get(0).reader());
      }
      try (DirectoryReader r = DirectoryReader.open(target)) {
        assertEquals(5, r.numDocs());
        StoredFields storedFields = r.storedFields();
        TermVectors termVectors = r.termVectors();
        List<String> ids = new ArrayList<>();
        for (int doc = 0; doc < r.maxDoc(); doc++) {
          String id = storedFields.document(doc).get("id");
          assertNotNull("doc " + doc + " lost its stored fields", id);
          assertNotNull("doc " + doc + " (" + id + ") lost its term vectors", termVectors.get(doc));
          ids.add(id);
        }
        ids.sort(null);
        assertEquals(List.of("a0", "a1", "a2", "b0", "b1"), ids);
      }
    } finally {
      a.close();
      b.close();
    }
  }

  /** Control: without reordering the same addIndexes call keeps every document intact. */
  public void testControlWithoutReordering() throws IOException {
    addAndCheck(new LogDocMergePolicy());
  }

  /** The stock reordering merge policy with default settings. */
  public void testBPReorderingMergePolicy() throws IOException {
    addAndCheck(new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer()));
  }
}
