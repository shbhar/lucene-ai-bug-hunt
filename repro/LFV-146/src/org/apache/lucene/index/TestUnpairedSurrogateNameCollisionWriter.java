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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Same root cause as TestUnpairedSurrogateNameCollision, seen from the writing session: after the
 * flushed segment with two fields both written as "f" + U+FFFD is committed, the writer's own next
 * delete application opens that segment, fails, and the writer closes with a tragic exception.
 */
public class TestUnpairedSurrogateNameCollisionWriter extends LuceneTestCase {

  public void testWriterKeepsWorkingAfterCommit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
      try {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.NO));
        doc.add(new StringField("f\uD800", "a", Field.Store.NO));
        doc.add(new StringField("f\uFFFD", "b", Field.Store.NO));
        w.addDocument(doc);
        w.commit(); // succeeds
        // fails on main: IllegalArgumentException: duplicate field names: 1 and 2 have: f\uFFFD,
        // and the writer is closed with that exception as its tragic exception
        try {
          w.deleteDocuments(new Term("id", "1"));
          w.commit();
        } finally {
          assertNull("tragic exception: " + w.getTragicException(), w.getTragicException());
        }
      } finally {
        w.close();
      }
    }
  }

  /** CheckIndex on the committed index (the commit itself succeeded). */
  public void testCheckIndexAfterCommit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        doc.add(new StringField("f\uD800", "a", Field.Store.NO));
        doc.add(new StringField("f\uFFFD", "b", Field.Store.NO));
        w.addDocument(doc);
        w.commit(); // succeeds
      }
      try (CheckIndex checker = new CheckIndex(dir)) {
        // fails on main: the segment's "open reader" step fails with
        // IllegalArgumentException: duplicate field names: 0 and 1 have: f\uFFFD
        CheckIndex.Status status = checker.checkIndex();
        assertTrue("CheckIndex reports the index as broken", status.clean);
      }
    }
  }
}
