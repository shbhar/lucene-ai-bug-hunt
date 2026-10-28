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
 * Reachability check with a moderate value size: one updateBinaryDocValue call with a 4 KiB value
 * whose term matches 530,000 documents of one segment (530,000 x 4,096 bytes > 2^31 - 1). Default
 * IndexWriterConfig (default merge policy, default RAM buffer). Expected per the javadoc: the update
 * is applied and the writer stays open.
 */
public class TestModerateValueBinaryUpdate extends LuceneTestCase {

  public void testModerateValueManyDocs() throws Exception {
    final int numDocs = Integer.getInteger("v3.numDocs", 530_000);
    final int valueLength = Integer.getInteger("v3.valueLength", 4096);
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(null));
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new StringField("tenant", "t1", Field.Store.NO));
        doc.add(new BinaryDocValuesField("blob", new BytesRef(new byte[] {(byte) i})));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      w.commit();
      int segs;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        segs = r.leaves().size();
      }
      byte[] v = new byte[valueLength];
      for (int i = 0; i < v.length; i++) {
        v[i] = (byte) (i * 13 + 1);
      }
      w.updateBinaryDocValue(new Term("tenant", "t1"), "blob", new BytesRef(v));
      Throwable failure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        failure = t;
      }
      System.out.println(
          "segments="
              + segs
              + " numDocs="
              + numDocs
              + " valueLength="
              + valueLength
              + " product="
              + ((long) numDocs * valueLength)
              + " failure="
              + failure
              + " writerOpen="
              + w.isOpen()
              + " tragic="
              + w.getTragicException());
      assertNull("commit after an accepted binary doc-values update threw: " + failure, failure);
      assertTrue("IndexWriter closed itself", w.isOpen());
      w.close();
    }
  }
}
