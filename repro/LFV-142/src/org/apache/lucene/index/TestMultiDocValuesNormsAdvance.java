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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * MultiDocValues.getNormValues(...).advance(target) returns a document before target when target
 * falls in a segment that has no norms for the field.
 */
public class TestMultiDocValuesNormsAdvance extends LuceneTestCase {

  /** Three segments: doc 0 has the field, docs 1-2 do not, doc 3 has the field. */
  private static Directory buildIndex() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(docWithField(0)); // segment 0: doc 0
      w.commit();
      w.addDocument(new Document()); // segment 1: docs 1 and 2, no "body" field
      w.addDocument(new Document());
      w.commit();
      w.addDocument(docWithField(3)); // segment 2: doc 3
      w.commit();
    }
    return dir;
  }

  private static Document docWithField(int value) {
    Document doc = new Document();
    doc.add(new TextField("body", "some text", Field.Store.NO));
    doc.add(new NumericDocValuesField("dv", value));
    return doc;
  }

  public void testNormsAdvanceIntoSegmentWithoutNorms() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(3, reader.leaves().size());
      NumericDocValues norms = MultiDocValues.getNormValues(reader, "body");
      assertNotNull(norms);
      // DocIdSetIterator#advance: the first document >= target (docs 1 and 2 have no norms, so 3)
      int doc = norms.advance(1);
      assertEquals("advance(1) must not return a doc before the target", 3, doc);
    }
  }

  public void testNormsAdvanceIntoLastSegmentWithoutNorms() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(docWithField(0)); // segment 0: doc 0 has norms
      w.commit();
      w.addDocument(new Document()); // segment 1: doc 1 has no "body" field
      w.commit();
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      NumericDocValues norms = MultiDocValues.getNormValues(reader, "body");
      assertEquals(DocIdSetIterator.NO_MORE_DOCS, norms.advance(1));
    } finally {
      dir.close();
    }
  }

  /** Control: the numeric doc-values view over the same layout answers correctly. */
  public void testNumericDocValuesControl() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      NumericDocValues dv = MultiDocValues.getNumericValues(reader, "dv");
      assertEquals(3, dv.advance(1));
      assertEquals(3, dv.longValue());
    }
  }
}
