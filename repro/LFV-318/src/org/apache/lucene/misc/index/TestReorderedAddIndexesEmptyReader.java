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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReorderedAddIndexesEmptyReader extends LuceneTestCase {

  public void testDocAfterEmptyReaderKeepsStoredFields() throws IOException {
    try (Directory a = oneDocIndex("a");
        Directory b = oneDocIndex("b");
        Directory target = new ByteBuffersDirectory();
        DirectoryReader ra = DirectoryReader.open(a);
        DirectoryReader rb = DirectoryReader.open(b)) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(
                  new BPReorderingMergePolicy(new LogDocMergePolicy(), new BPIndexReorderer()));
      try (IndexWriter w = new IndexWriter(target, iwc)) {
        w.addIndexes(
            (CodecReader) ra.leaves().get(0).reader(),
            SlowCodecReaderWrapper.wrap(new ParallelLeafReader()),
            (CodecReader) rb.leaves().get(0).reader());
      }
      try (DirectoryReader r = DirectoryReader.open(target)) {
        int withStoredId = 0;
        for (int doc = 0; doc < r.maxDoc(); doc++) {
          if (r.storedFields().document(doc).get("id") != null) {
            withStoredId++;
          }
        }
        // expected: both keep their stored id; actual: the doc after the empty reader has none
        assertEquals("documents with a stored id, of maxDoc " + r.maxDoc(), 2, withStoredId);
      }
    }
  }

  private static Directory oneDocIndex(String id) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StoredField("id", id));
      w.addDocument(doc);
    }
    return dir;
  }
}
