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
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * The numeric, binary and norms views of MultiDocValues throw NullPointerException from
 * advance(target) after advanceExact landed on a document of a segment that has no values for the
 * field, when target is in that same segment.
 */
public class TestMultiDocValuesAdvanceAfterAdvanceExact extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(new Document()); // segment 0: docs 0 and 1 have none of the fields
      w.addDocument(new Document());
      w.commit();
      Document doc = new Document(); // segment 1: doc 2 has all of them
      doc.add(new TextField("body", "some text", Field.Store.NO));
      doc.add(new NumericDocValuesField("num", 42));
      doc.add(new BinaryDocValuesField("bin", new BytesRef("v")));
      doc.add(new SortedNumericDocValuesField("snum", 42));
      w.addDocument(doc);
      w.commit();
    }
    reader = DirectoryReader.open(dir);
    assertEquals(2, reader.leaves().size());
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  // Each test: advanceExact(0) -> false (doc 0 has no value), then advance(1): docID() is 0 and
  // 1 > 0, so per DocIdSetIterator#advance the result is the first doc >= 1 with a value, i.e. 2.

  public void testNumeric() throws IOException {
    NumericDocValues dv = MultiDocValues.getNumericValues(reader, "num");
    assertFalse(dv.advanceExact(0));
    assertEquals(0, dv.docID());
    assertEquals(2, dv.advance(1));
    assertEquals(42, dv.longValue());
  }

  public void testBinary() throws IOException {
    BinaryDocValues dv = MultiDocValues.getBinaryValues(reader, "bin");
    assertFalse(dv.advanceExact(0));
    assertEquals(2, dv.advance(1));
    assertEquals(new BytesRef("v"), dv.binaryValue());
  }

  public void testNorms() throws IOException {
    NumericDocValues norms = MultiDocValues.getNormValues(reader, "body");
    assertFalse(norms.advanceExact(0));
    assertEquals(2, norms.advance(1));
  }

  /** Control: the sorted-numeric view (which uses empty, non-null per-leaf iterators) is fine. */
  public void testSortedNumericControl() throws IOException {
    SortedNumericDocValues dv = MultiDocValues.getSortedNumericValues(reader, "snum");
    assertFalse(dv.advanceExact(0));
    assertEquals(2, dv.advance(1));
    assertEquals(42, dv.nextValue());
  }

  /** Control: nextDoc() after the same advanceExact is fine on the numeric view. */
  public void testNumericNextDocControl() throws IOException {
    NumericDocValues dv = MultiDocValues.getNumericValues(reader, "num");
    assertFalse(dv.advanceExact(0));
    assertEquals(2, dv.nextDoc());
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, dv.nextDoc());
  }
}
