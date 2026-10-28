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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The first doc-values update of a field in a buffered-updates packet is stored twice: the
 * FieldUpdatesBuffer constructor records it, and BufferedUpdates then adds it again. Public API
 * only.
 */
public class TestFirstDocValuesUpdateBufferedTwice extends LuceneTestCase {

  /**
   * Three binary updates of the same field with 1 MiB values and terms that match nothing: each
   * should add about 1 MiB to the writer's RAM accounting. The first one adds twice as much.
   */
  public void testFirstUpdateIsAccountedOnce() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir, new IndexWriterConfig(null).setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document doc = new Document();
      doc.add(new StringField("id", "x", Field.Store.NO));
      doc.add(new BinaryDocValuesField("payload", new BytesRef("v")));
      doc.add(new NumericDocValuesField("num", 1));
      w.addDocument(doc);
      w.commit();
      final int len = 1 << 20;
      long r0 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "none-1"), "payload", new BytesRef(new byte[len]));
      long r1 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "none-2"), "payload", new BytesRef(new byte[len]));
      long r2 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "none-3"), "payload", new BytesRef(new byte[len]));
      long r3 = w.ramBytesUsed();
      long first = r1 - r0, second = r2 - r1, third = r3 - r2;
      System.out.println(
          "RAM accounted per 1 MiB binary update: first=" + first + " second=" + second + " third=" + third);
      assertTrue("second=" + second, second >= len && second < len + len / 4);
      assertTrue(
          "the first update of the field is accounted "
              + first
              + " bytes, about twice the "
              + second
              + " of each later one",
          first < second + len / 4);
    }
  }

  /**
   * The duplicate is also applied: one binary update of a 1 MiB value matching 1,100 documents of
   * one segment (1.07 GiB of values) closes the writer when it is the first update of its field
   * in the packet, and commits fine when another update of the field (matching nothing) comes
   * first. Needs -Xmx8g. Select with -Dh321.precede=true for the passing control.
   */
  public void testFirstBinaryUpdateIsAppliedOnce() throws Exception {
    final int numDocs = 1100;
    final boolean precede = Boolean.getBoolean("h321.precede");
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig(null).setMergePolicy(NoMergePolicy.INSTANCE));
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new StringField("group", "g", Field.Store.NO));
        doc.add(new BinaryDocValuesField("payload", new BytesRef(new byte[] {(byte) i})));
        w.addDocument(doc);
      }
      w.commit();
      if (precede) {
        w.updateBinaryDocValue(new Term("group", "none"), "payload", new BytesRef("x"));
      }
      w.updateBinaryDocValue(new Term("group", "g"), "payload", new BytesRef(new byte[1 << 20]));
      Throwable failure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        failure = t;
      }
      System.out.println(
          "precede=" + precede + " commit failure: " + failure + "; writer open: " + w.isOpen());
      assertNull("commit failed: " + failure, failure);
      assertTrue(w.isOpen());
      w.close();
    }
  }
}
