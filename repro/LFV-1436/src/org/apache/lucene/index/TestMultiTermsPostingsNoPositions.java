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
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestMultiTermsPostingsNoPositions extends LuceneTestCase {

  public void testNullWhenPositionsNotIndexed() throws Exception {
    // Javadoc: null when positions were not indexed; actual: a PostingsEnum whose nextPosition() is -1.
    FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new Field("f", "a", type));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        PostingsEnum pe = MultiTerms.getTermPostingsEnum(r, "f", new BytesRef("a"));
        if (pe != null) {
          pe.nextDoc();
          fail("expected null, got a PostingsEnum with nextPosition()=" + pe.nextPosition());
        }
      }
    }
  }
}
