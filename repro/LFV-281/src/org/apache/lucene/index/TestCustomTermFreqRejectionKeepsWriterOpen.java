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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCustomTermFreqRejectionKeepsWriterOpen extends LuceneTestCase {

  public void testTermVectorPositionsRejectionKeepsWriterOpen() throws Exception {
    FieldType ft = new FieldType();
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    Token token = new Token("a", 0, 1);
    token.setTermFrequency(2);
    Document doc = new Document();
    doc.add(new Field("f", new CannedTokenStream(token), ft));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(doc));
      // expected: only the document is rejected; actual: its exception closes the writer as tragic
      assertNull("tragic exception", w.getTragicException());
    }
  }
}
