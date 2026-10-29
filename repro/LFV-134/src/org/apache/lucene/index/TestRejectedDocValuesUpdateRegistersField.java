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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestRejectedDocValuesUpdateRegistersField extends LuceneTestCase {

  public void testRejectedUpdateDoesNotDefineField() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // SORTED doc values cannot be updated, so this call is rejected
      expectThrows(
          IllegalArgumentException.class,
          () ->
              w.updateDocValues(
                  new Term("id", "0"), new SortedDocValuesField("tag", new BytesRef("a"))));

      Document doc = new Document();
      doc.add(new StringField("tag", "a", Field.Store.NO));
      doc.add(new SortedDocValuesField("tag", new BytesRef("a")));
      try {
        w.addDocument(doc);
      } catch (IllegalArgumentException e) {
        fail("expected: the first document with \"tag\" is accepted; actual: " + e.getMessage());
      }
    }
  }
}
