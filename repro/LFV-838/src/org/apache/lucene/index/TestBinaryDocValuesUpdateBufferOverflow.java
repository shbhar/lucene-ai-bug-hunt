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

import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * One {@link IndexWriter#updateBinaryDocValue} call whose term matches 2,100 documents of one
 * segment, with a 1 MiB value. Applying it keeps one copy of the value per matched document in a
 * single int-addressed buffer (BinaryDocValuesFieldUpdates), which overflows past 2^31 bytes; the
 * failure is handled as a tragic event and the writer closes.
 *
 * <p>Public API only. Needs a large heap (run with -Xmx8g): the failing path allocates about 4 GB.
 * Set -Dh321.numDocs=N to change the number of matched documents.
 */
public class TestBinaryDocValuesUpdateBufferOverflow extends LuceneTestCase {

  public void testBinaryUpdateMatchingManyDocsKeepsWriterOpen() throws Exception {
    final int numDocs = Integer.getInteger("h321.numDocs", 2100);
    final int valueLength = 1 << 20; // 1 MiB
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(null);
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      IndexWriter w = new IndexWriter(dir, iwc);
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new StringField("group", "g", Field.Store.NO));
        doc.add(new BinaryDocValuesField("payload", new BytesRef(new byte[] {(byte) i})));
        w.addDocument(doc);
      }
      w.commit(); // one segment with numDocs documents

      byte[] big = new byte[valueLength];
      for (int i = 0; i < big.length; i++) {
        big[i] = (byte) (i * 31 + 7);
      }
      // an unrelated document, acknowledged before the update and not yet committed
      Document late = new Document();
      late.add(new StringField("group", "late", Field.Store.NO));
      w.addDocument(late);
      w.updateBinaryDocValue(new Term("group", "g"), "payload", new BytesRef(big));
      Throwable failure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        failure = t;
      }
      System.out.println(
          "numDocs="
              + numDocs
              + " commit failure: "
              + failure
              + "; writer open: "
              + w.isOpen()
              + "; tragic exception: "
              + w.getTragicException());
      if (w.isOpen() == false) {
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          System.out.println(
              "after the close: committed docs="
                  + r.numDocs()
                  + ", acknowledged late doc present="
                  + (r.docFreq(new Term("group", "late")) > 0));
        }
      }
      assertNull("commit of an accepted binary doc-values update threw: " + failure, failure);
      assertTrue("IndexWriter closed itself", w.isOpen());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        BinaryDocValues bdv = MultiDocValues.getBinaryValues(r, "payload");
        assertEquals(numDocs + 1, r.numDocs());
        for (int d = 0; d < numDocs; d++) {
          assertTrue(bdv.advanceExact(d));
          assertEquals(new BytesRef(big), bdv.binaryValue());
        }
      }
      w.close();
    }
  }
}
