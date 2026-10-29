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
import org.apache.lucene.document.InvertableType;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBinaryInvertableTypeNorm extends LuceneTestCase {

  public void testSingleValueNorm() throws Exception {
    FieldType type = new FieldType();
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    type.setTokenized(false);
    Document doc = new Document();
    doc.add(
        new Field("tag", new BytesRef("x"), type) {
          @Override
          public InvertableType invertableType() {
            return InvertableType.BINARY;
          }
        });
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        NumericDocValues norms = getOnlyLeafReader(reader).getNormValues("tag");
        assertTrue(norms.advanceExact(0));
        // one value = field length 1, so the BM25 norm should be 1; actual 2
        assertEquals(1, norms.longValue());
      }
    }
  }
}
