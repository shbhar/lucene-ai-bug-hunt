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
package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * addIndexes(CodecReader...) reserves only the live documents of its inputs (it sums numDocs()),
 * and its javadoc says it throws IllegalArgumentException only "if addIndexes would cause the
 * index to exceed MAX_DOCS". Yet with the default configuration it builds a concatenated view of
 * the inputs (SlowCompositeCodecReaderWrapper, through a MultiReader) whose total maxDoc(),
 * deleted documents included, must not exceed MAX_DOCS, so a call that adds few live documents
 * fails when the inputs have many deleted documents. The limit is lowered with the test
 * framework's setIndexWriterMaxDocs (as TestIndexWriterMaxDocs does); at full scale the same
 * happens for inputs whose maxDoc() sum exceeds IndexWriter.MAX_DOCS = 2^31 - 129.
 */
public class TestAddIndexesReadersWithManyDeletions extends LuceneTestCase {

  /** One segment with 8 documents, 6 of them deleted (maxDoc 8, numDocs 2). */
  private static Directory buildIndex(String prefix) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 8; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", prefix + i, Field.Store.YES));
        w.addDocument(doc);
      }
      w.commit();
      for (int i = 0; i < 6; i++) {
        w.deleteDocuments(new Term("id", prefix + i));
      }
      w.commit();
    }
    return dir;
  }

  private void run(boolean oneCall) throws IOException {
    Directory a = buildIndex("a");
    Directory b = buildIndex("b");
    setIndexWriterMaxDocs(10);
    try (DirectoryReader ra = DirectoryReader.open(a);
        DirectoryReader rb = DirectoryReader.open(b);
        Directory target = newDirectory()) {
      CodecReader ca = (CodecReader) ra.leaves().get(0).reader();
      CodecReader cb = (CodecReader) rb.leaves().get(0).reader();
      assertEquals(8, ca.maxDoc());
      assertEquals(2, ca.numDocs());
      try (IndexWriter w = new IndexWriter(target, new IndexWriterConfig())) {
        if (oneCall) {
          w.addIndexes(ca, cb); // 4 live documents, limit is 10
        } else {
          w.addIndexes(ca);
          w.addIndexes(cb);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(target)) {
        assertEquals(4, r.numDocs());
      }
    } finally {
      restoreIndexWriterMaxDocs();
      a.close();
      b.close();
    }
  }

  /** Control: adding the same readers one call at a time works. */
  public void testControlOneReaderPerCall() throws IOException {
    run(false);
  }

  /** Adding both in one call throws "Too many documents: composite IndexReaders cannot exceed". */
  public void testBothReadersInOneCall() throws IOException {
    run(true);
  }
}
