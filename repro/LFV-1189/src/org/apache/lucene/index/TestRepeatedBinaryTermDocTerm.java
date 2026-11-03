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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestRepeatedBinaryTermDocTerm extends LuceneTestCase {

  public void testRepeatedNonUtf8TermDoesNotCloseWriter() throws Exception {
    FieldType ft = new FieldType();
    ft.setTokenized(false);
    ft.setIndexOptions(IndexOptions.DOCS_AND_CUSTOM_FREQS);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new Field("f", new BytesRef(new byte[] {(byte) 0xC3}), ft));
      doc.add(new Field("f", new BytesRef(new byte[] {(byte) 0xC3}), ft));
      // Expected: IAE, writer stays open; actual: utf8ToString throws and closes the writer.
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(doc));
      assertTrue(w.isOpen());
    }
  }
}
