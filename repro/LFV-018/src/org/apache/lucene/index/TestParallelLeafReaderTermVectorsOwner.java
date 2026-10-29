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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParallelLeafReaderTermVectorsOwner extends LuceneTestCase {

  private static DirectoryReader oneDocIndex(Directory dir, String body, FieldType type)
      throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new Field("body", body, type));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  public void testTermVectorsOnlyFromFirstReaderHavingTheField() throws IOException {
    FieldType withVectors = new FieldType(TextField.TYPE_NOT_STORED);
    withVectors.setStoreTermVectors(true);
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        DirectoryReader first = oneDocIndex(d1, "first", TextField.TYPE_NOT_STORED);
        DirectoryReader second = oneDocIndex(d2, "second", withVectors);
        ParallelLeafReader reader =
            new ParallelLeafReader(false, getOnlyLeafReader(first), getOnlyLeafReader(second))) {
      Fields vectors = reader.termVectors().get(0);
      Terms body = vectors == null ? null : vectors.terms("body");
      // expected: none, the first reader owns "body" and has no vectors; actual: the second's
      assertNull(
          "term vector of body", body == null ? null : body.iterator().next().utf8ToString());
    }
  }
}
