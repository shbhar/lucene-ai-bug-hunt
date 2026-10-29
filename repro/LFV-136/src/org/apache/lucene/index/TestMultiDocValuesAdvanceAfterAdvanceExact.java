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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiDocValuesAdvanceAfterAdvanceExact extends LuceneTestCase {

  public void testAdvanceAfterAdvanceExactOnSegmentWithoutField() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(new Document());
      w.addDocument(new Document());
      w.commit();
      Document doc = new Document();
      doc.add(new NumericDocValuesField("f", 42));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        NumericDocValues dv = MultiDocValues.getNumericValues(reader, "f");
        assertFalse(dv.advanceExact(0));
        int next;
        try {
          next = dv.advance(1);
        } catch (NullPointerException e) {
          // segment 0 (docs 0, 1) lacks the field: expected advance(1) == 2, actual NPE
          throw new AssertionError("expected advance(1) == 2, got " + e, e);
        }
        assertEquals(2, next);
      }
    }
  }
}
