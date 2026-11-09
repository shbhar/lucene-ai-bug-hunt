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
package org.apache.lucene.store;

import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNRTCachingAddIndexes extends LuceneTestCase {

  public void testAddIndexesRespectsMaxMergeSize() throws Exception {
    byte[] blob = new byte[2 * 1024 * 1024];
    new Random(0).nextBytes(blob);
    try (Directory src = new ByteBuffersDirectory();
        NRTCachingDirectory dir =
            new NRTCachingDirectory(FSDirectory.open(createTempDir()), 1.0, 60.0)) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StoredField("blob", blob));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addIndexes((CodecReader) r.leaves().get(0).reader());
        // expected: a ~2 MB merge is not cached (maxMergeSizeMB 1.0); actual: all of it is
        assertTrue("cached " + dir.ramBytesUsed() + " bytes", dir.ramBytesUsed() <= 1024 * 1024);
      }
    }
  }
}
