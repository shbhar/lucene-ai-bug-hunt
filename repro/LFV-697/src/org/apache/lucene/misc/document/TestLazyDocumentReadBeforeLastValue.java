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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LazyDocument.getField re-fetches the document when more lazy fields are requested after others
 * were already loaded (comment in getField). For a multi-valued field, reading the first lazy value
 * before the lazy field of the second value has been requested trips the assertion in
 * fetchRealValues: it asserts {@code realValues.length <= lazyValues.size()}, while its message
 * ("More lazy values then real values") describes the opposite condition. With assertions disabled
 * the same calls return the right values.
 */
public class TestLazyDocumentReadBeforeLastValue extends LuceneTestCase {

  public void testReadFirstValueBeforeRequestingSecond() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d = new Document();
        d.add(new StoredField("f", "v0"));
        d.add(new StoredField("f", "v1"));
        w.addDocument(d);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        FieldInfo fi = reader.leaves().get(0).reader().getFieldInfos().fieldInfo("f");
        LazyDocument lazy = new LazyDocument(reader, 0);

        IndexableField first = lazy.getField(fi);
        assertEquals("v0", first.stringValue()); // AssertionError here with -ea

        IndexableField second = lazy.getField(fi);
        assertEquals("v1", second.stringValue());
        assertEquals("v0", first.stringValue());
      }
    }
  }
}
