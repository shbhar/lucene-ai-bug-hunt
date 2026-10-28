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
import java.util.Arrays;
import java.util.function.Supplier;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFieldDataInput;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A stored field read back from a MemoryIndex should have the same value and type as the same field
 * read back from an IndexWriter index.
 */
public class TestMemoryIndexStoredValueParity extends LuceneTestCase {

  public void testStringField() throws IOException {
    Supplier<IndexableField> f = () -> new StringField("id", "doc-1", Field.Store.YES);
    assertEquals("doc-1", viaIndexWriter(f).get("id"));
    assertEquals("doc-1", viaMemoryIndex(f).get("id"));
  }

  public void testKeywordField() throws IOException {
    Supplier<IndexableField> f = () -> new KeywordField("tag", "red", Field.Store.YES);
    assertEquals("red", viaIndexWriter(f).get("tag"));
    assertEquals("red", viaMemoryIndex(f).get("tag"));
  }

  public void testIntField() throws IOException {
    assertParity("Integer(5)", () -> new IntField("v", 5, Field.Store.YES));
  }

  public void testLongField() throws IOException {
    assertParity("Long(7)", () -> new LongField("v", 7L, Field.Store.YES));
  }

  public void testFloatField() throws IOException {
    assertParity("Float(1.5)", () -> new FloatField("v", 1.5f, Field.Store.YES));
  }

  public void testDoubleField() throws IOException {
    assertParity("Double(2.5)", () -> new DoubleField("v", 2.5d, Field.Store.YES));
  }

  public void testStoredFieldDataInput() throws IOException {
    assertParity(
        "binary[9, 8, 7]",
        () ->
            new StoredField(
                "v", new StoredFieldDataInput(new ByteArrayDataInput(new byte[] {9, 8, 7}))));
  }

  /** Controls: these already agree. */
  public void testControls() throws IOException {
    assertParity("binary[97]", () -> new StringField("v", new BytesRef("a"), Field.Store.YES));
    assertParity("String(a)", () -> new StoredField("v", "a"));
    assertParity("Integer(5)", () -> new StoredField("v", 5));
  }

  /** Asserts the IndexWriter value first, so a wrong expectation fails on its own line. */
  private static void assertParity(String expected, Supplier<IndexableField> field)
      throws IOException {
    assertEquals("IndexWriter", expected, describe(viaIndexWriter(field)));
    assertEquals("MemoryIndex", expected, describe(viaMemoryIndex(field)));
  }

  private static Document viaIndexWriter(Supplier<IndexableField> field) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(field.get());
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return r.storedFields().document(0);
      }
    }
  }

  private static Document viaMemoryIndex(Supplier<IndexableField> field) throws IOException {
    Document doc = new Document();
    doc.add(field.get());
    MemoryIndex mi = MemoryIndex.fromDocument(doc, new StandardAnalyzer());
    return mi.createSearcher().storedFields().document(0);
  }

  /** The stored value of the document's only field, with its type. */
  private static String describe(Document d) {
    if (d.getFields().isEmpty()) {
      return "<absent>";
    }
    IndexableField f = d.getFields().get(0);
    if (f.numericValue() != null) {
      return f.numericValue().getClass().getSimpleName() + "(" + f.numericValue() + ")";
    } else if (f.binaryValue() != null) {
      BytesRef b = f.binaryValue();
      return "binary" + Arrays.toString(Arrays.copyOfRange(b.bytes, b.offset, b.offset + b.length));
    } else {
      return "String(" + f.stringValue() + ")";
    }
  }
}
