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

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFailedAddIndexesRegistersField extends LuceneTestCase {

  public void testFailedAddIndexesDoesNotDefineField() throws Exception {
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig())) {
        sw.addDocument(
            List.of(
                new StringField("a_new", "x", Field.Store.NO),
                new StringField("z_conflict", "x", Field.Store.NO)));
      }
      try (IndexWriter w = new IndexWriter(dst, new IndexWriterConfig())) {
        w.addDocument(List.of(new NumericDocValuesField("z_conflict", 1)));
        // "z_conflict" is doc-values only in dst, so nothing from src is added
        expectThrows(IllegalArgumentException.class, () -> w.addIndexes(src));

        try {
          w.addDocument(List.of(new NumericDocValuesField("a_new", 5)));
        } catch (IllegalArgumentException e) {
          fail("expected: the first document with \"a_new\" is accepted; actual: " + e.getMessage());
        }
      }
    }
  }
}
