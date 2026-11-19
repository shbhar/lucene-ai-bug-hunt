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
package org.apache.lucene.codecs.lucene90.compressing;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSevenDocumentsAreOneSegment extends LuceneTestCase {

  public void testOneSegment() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter writer =
            new IndexWriter(dir, newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 7; i++) {
        writer.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        // expected: 1, as both testDictionaryReuse tests assert; actual with seed 7D797B773AD37D33: 2
        assertEquals(1, reader.leaves().size());
      }
    }
  }
}
