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

import java.lang.ref.WeakReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIdleSegmentPinsAppliedDeletes extends LuceneTestCase {

  // Expected: deletes pushed past the 1 MB RAM buffer are released; actual: one buffered doc keeps them reachable.
  public void testBufferedDocumentPinsPushedDeletes() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setRAMBufferSizeMB(1))) {
      Document doc = new Document();
      doc.add(new StringField("id", "buffered", Field.Store.NO));
      w.addDocument(doc);
      Term first = new Term("id", "x0");
      WeakReference<Term> firstRef = new WeakReference<>(first);
      w.deleteDocuments(first);
      first = null;
      for (int i = 1; i <= 100_000; i++) {
        w.deleteDocuments(new Term("id", "x" + i));
      }
      for (int i = 0; i < 10 && firstRef.get() != null; i++) {
        System.gc();
      }
      assertNull("first delete term still reachable after 100,000 deletes", firstRef.get());
    }
  }
}
