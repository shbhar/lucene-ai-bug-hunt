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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Probe (prints facts, asserts the buggy outcome): what happens to the documents around a slice
 * that ends at the end of its array. Passes on unfixed Lucene when the writer closes and the
 * uncommitted document is lost.
 */
public class TestStoredFieldDataInputLostDocsProbe extends LuceneTestCase {

  public void testUncommittedDocumentIsLost() throws Exception {
    byte[] buf = new byte[12];
    try (Directory dir = newDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, newIndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
      Document committed = new Document();
      committed.add(new StoredField("f", new BytesRef(new byte[] {1})));
      w.addDocument(committed);
      w.commit();
      Document buffered = new Document();
      buffered.add(new StoredField("f", new BytesRef(new byte[] {2})));
      w.addDocument(buffered);
      Document slice = new Document();
      slice.add(new StoredField("f", new StoredFieldDataInput(new ByteArrayDataInput(buf, 4, 8))));
      Throwable t = null;
      try {
        w.addDocument(slice);
      } catch (Throwable e) {
        t = e;
      }
      System.out.println("PROBE addDocument threw: " + t);
      System.out.println("PROBE isOpen=" + w.isOpen() + " tragic=" + w.getTragicException());
      Throwable next = null;
      try {
        Document after = new Document();
        after.add(new StoredField("f", new BytesRef(new byte[] {3})));
        w.addDocument(after);
      } catch (Throwable e) {
        next = e;
      }
      System.out.println("PROBE next addDocument threw: " + next);
      int numDocs;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        numDocs = r.numDocs();
      }
      System.out.println("PROBE committed index numDocs=" + numDocs + " (2 docs were added before the slice)");
      w.close();
      assertNotNull(t);
      assertFalse(w.isOpen());
      assertTrue(next instanceof AlreadyClosedException);
      assertEquals(1, numDocs);
    }
  }
}
