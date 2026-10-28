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
package org.apache.lucene.index.memory;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Since GITHUB#11999 MemoryIndex stores stored fields. IndexWriter stores {@link
 * IndexableField#storedValue()}; MemoryIndex instead stores binaryValue(), else numericValue(),
 * else stringValue(). StringField, KeywordField and IntField/LongField/FloatField/DoubleField
 * return a BytesRef from binaryValue() (the indexed term or the encoded point), so with
 * Field.Store.YES their stored value comes back from MemoryIndex as binary instead of the String
 * or number IndexWriter returns.
 */
public class TestMemoryIndexStoredValues extends LuceneTestCase {

  private static Document doc() {
    Document doc = new Document();
    doc.add(new StringField("id", "doc-1", Field.Store.YES));
    doc.add(new KeywordField("tag", "red", Field.Store.YES));
    doc.add(new IntField("count", 5, Field.Store.YES));
    return doc;
  }

  public void testStoredValuesMatchIndexWriter() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    Document fromIndexWriter;
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        fromIndexWriter = r.storedFields().document(0);
      }
    }
    assertEquals("doc-1", fromIndexWriter.get("id"));
    assertEquals("red", fromIndexWriter.get("tag"));
    assertEquals(5, fromIndexWriter.getField("count").numericValue());

    Document fromMemoryIndex =
        MemoryIndex.fromDocument(doc(), analyzer).createSearcher().storedFields().document(0);
    assertEquals("StringField(Store.YES)", fromIndexWriter.get("id"), fromMemoryIndex.get("id"));
    assertEquals("KeywordField(Store.YES)", fromIndexWriter.get("tag"), fromMemoryIndex.get("tag"));
    assertEquals(
        "IntField(Store.YES)",
        fromIndexWriter.getField("count").numericValue(),
        fromMemoryIndex.getField("count").numericValue());
  }
}
