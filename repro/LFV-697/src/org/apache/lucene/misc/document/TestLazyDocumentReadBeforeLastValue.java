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
package org.apache.lucene.misc.document;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLazyDocumentReadBeforeLastValue extends LuceneTestCase {

  public void testReadFirstValueBeforeRequestingSecond() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StoredField("f", "v0"));
        doc.add(new StoredField("f", "v1"));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        FieldInfo fi = FieldInfos.getMergedFieldInfos(reader).fieldInfo("f");
        LazyDocument lazy = new LazyDocument(reader, 0);
        // expected v0 then v1; actual (-ea): AssertionError from fetchRealValues on the first read
        assertEquals("v0", lazy.getField(fi).stringValue());
        assertEquals("v1", lazy.getField(fi).stringValue());
      }
    }
  }
}
