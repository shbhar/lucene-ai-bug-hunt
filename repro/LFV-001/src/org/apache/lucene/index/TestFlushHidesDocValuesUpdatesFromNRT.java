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
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** IndexWriter#flush writes resolved DV updates without bumping SIS.version. */
public class TestFlushHidesDocValuesUpdatesFromNRT extends LuceneTestCase {

  private static IndexWriter writer(Directory dir, boolean softDeletes) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler());
    if (softDeletes) {
      iwc.setSoftDeletesField("soft_delete");
    }
    IndexWriter w = new IndexWriter(dir, iwc);
    // two docs, so that deleting one does not drop the segment as 100% deleted
    for (String id : new String[] {"1", "2"}) {
      Document doc = new Document();
      doc.add(new StringField("id", id, Field.Store.NO));
      doc.add(new NumericDocValuesField("val", 1));
      w.addDocument(doc);
    }
    return w;
  }

  /** A soft delete applied by flush() is missed by openIfChanged. */
  public void testSoftDeleteThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true)) {
      DirectoryReader r = DirectoryReader.open(w); // the docs are now in a flushed segment
      assertEquals(2, r.numDocs());
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("soft_delete", 1));
      w.flush();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      try (DirectoryReader fresh = DirectoryReader.open(w)) {
        assertEquals("a fresh NRT reader sees the soft delete", 1, fresh.numDocs());
      }
      assertNotNull("openIfChanged must not return null: there is a new soft delete", r2);
      r2.close();
      r.close();
    }
  }

  /** Same with a plain numeric doc-values update. */
  public void testNumericUpdateThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateNumericDocValue(new Term("id", "1"), "val", 2);
      w.flush();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      assertNotNull("openIfChanged must not return null: there is a new DV update", r2);
      NumericDocValues v = r2.leaves().get(0).reader().getNumericDocValues("val");
      assertTrue(v.advanceExact(0));
      assertEquals(2, v.longValue());
      r2.close();
      r.close();
    }
  }

  /** Control: without the flush() in between, the reopen sees the update. */
  public void testSoftDeleteNoFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("soft_delete", 1));
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      assertNotNull(r2);
      assertEquals(1, r2.numDocs());
      r2.close();
      r.close();
    }
  }

  /** Control: a hard delete applied by flush() bumps the version and is seen. */
  public void testHardDeleteThenFlush() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, false)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.deleteDocuments(new Term("id", "1"));
      w.flush();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      assertNotNull(r2);
      assertEquals(1, r2.numDocs());
      r2.close();
      r.close();
    }
  }

  /** Control: commit() bumps the version after writing DV updates. */
  public void testSoftDeleteThenCommit() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, true)) {
      DirectoryReader r = DirectoryReader.open(w);
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("soft_delete", 1));
      w.commit();
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
      assertNotNull(r2);
      assertEquals(1, r2.numDocs());
      r2.close();
      r.close();
    }
  }
}
