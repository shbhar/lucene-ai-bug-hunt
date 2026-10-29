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
import org.apache.lucene.util.BytesRefBuilder;

public class TestBinaryDocValuesUpdateReusedBytesRef extends LuceneTestCase {

  public void testReusedBytesRefBuilder() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String id : new String[] {"0", "1"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        doc.add(new BinaryDocValuesField("bdv", new BytesRef("old")));
        writer.addDocument(doc);
      }
      BytesRefBuilder scratch = new BytesRefBuilder();
      scratch.copyChars("new0");
      writer.updateBinaryDocValue(new Term("id", "0"), "bdv", scratch.get());
      scratch.copyChars("new1");
      writer.updateBinaryDocValue(new Term("id", "1"), "bdv", scratch.get());
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BinaryDocValues values = getOnlyLeafReader(reader).getBinaryDocValues("bdv");
        assertTrue(values.advanceExact(0));
        // expected doc 0 to get the value passed with its update; actual: the scratch's later "new1"
        assertEquals("new0", values.binaryValue().utf8ToString());
      }
    }
  }
}
