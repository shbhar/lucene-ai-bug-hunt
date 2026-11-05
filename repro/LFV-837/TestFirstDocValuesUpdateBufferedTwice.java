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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestFirstDocValuesUpdateBufferedTwice extends LuceneTestCase {

  // Two 1 MiB binary updates of the same field, with terms that match no document: each should
  // add about 1 MiB to ramBytesUsed(). The first one is buffered twice and adds about 2 MiB.
  public void testFirstUpdateIsAccountedOnce() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new BinaryDocValuesField("payload", new BytesRef("v")));
      w.addDocument(doc);
      w.commit();
      BytesRef value = new BytesRef(new byte[1 << 20]);
      long r0 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "a"), "payload", value);
      long r1 = w.ramBytesUsed();
      w.updateBinaryDocValue(new Term("id", "b"), "payload", value);
      long r2 = w.ramBytesUsed();
      long first = r1 - r0, second = r2 - r1;
      assertTrue("second=" + second, second >= value.length);
      assertTrue("first=" + first + " second=" + second, first < second + value.length / 4);
    }
  }
}
