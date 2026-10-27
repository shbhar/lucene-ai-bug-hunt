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
import java.util.Set;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#addIndexes(Directory...) is documented as transactional: if it fails, no index is
 * added. When it fails on a schema conflict, the fields of the incoming index that were checked
 * before the conflicting one stay registered in the writer's index-wide schema, so the writer
 * reports field names that no segment has, and rejects valid documents for those names. Reopening
 * the writer forgets them again. addIndexes(CodecReader...) checks all fields first and does not
 * have this problem.
 *
 * <p>Only public API is used.
 */
public class TestFailedAddIndexesLeavesSchema extends LuceneTestCase {

  public void testFailedAddIndexesDoesNotChangeSchema() throws Exception {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig())) {
        // "a_new" gets field number 0 and is checked first; "z_conflict" clashes with dst
        sw.addDocument(
            List.of(
                new StringField("a_new", "x", Field.Store.NO),
                new StringField("z_conflict", "x", Field.Store.NO)));
      }
      try (IndexWriter w = new IndexWriter(dst, new IndexWriterConfig())) {
        w.addDocument(List.of(new NumericDocValuesField("z_conflict", 1)));
        w.commit();
        assertEquals(Set.of("z_conflict"), w.getFieldNames());

        // Correctly rejected: "z_conflict" is doc-values only in dst.
        expectThrows(IllegalArgumentException.class, () -> w.addIndexes(src));

        // Nothing was added, so the writer's schema must be unchanged ...
        assertEquals(Set.of("z_conflict"), w.getFieldNames());
        // ... and "a_new" must be free to be used with any schema.
        w.addDocument(List.of(new NumericDocValuesField("a_new", 5)));
        w.commit();
      }
    }
  }

  /** Control: the same documents are accepted once the writer is reopened. */
  public void testAcceptedAfterReopen() throws Exception {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig())) {
        sw.addDocument(
            List.of(
                new StringField("a_new", "x", Field.Store.NO),
                new StringField("z_conflict", "x", Field.Store.NO)));
      }
      try (IndexWriter w = new IndexWriter(dst, new IndexWriterConfig())) {
        w.addDocument(List.of(new NumericDocValuesField("z_conflict", 1)));
        w.commit();
        expectThrows(IllegalArgumentException.class, () -> w.addIndexes(src));
      }
      try (IndexWriter w = new IndexWriter(dst, new IndexWriterConfig())) {
        assertEquals(Set.of("z_conflict"), w.getFieldNames());
        w.addDocument(List.of(new NumericDocValuesField("a_new", 5)));
        w.commit();
      }
    }
  }
}
