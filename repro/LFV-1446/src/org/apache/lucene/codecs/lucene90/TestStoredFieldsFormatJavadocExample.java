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
package org.apache.lucene.codecs.lucene90;

import org.apache.lucene.backward_codecs.lucene100.Lucene100Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStoredFieldsFormatJavadocExample extends LuceneTestCase {

  // Expected: the class javadoc's BEST_COMPRESSION example indexes a document; actual: commit throws UnsupportedOperationException.
  public void testJavadocExample() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(new Lucene100Codec(Lucene100Codec.Mode.BEST_COMPRESSION));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));
        w.addDocument(doc);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
    }
  }
}
