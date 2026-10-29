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

public class TestTryUndeleteDuringMerge extends LuceneTestCase {

  public void testUndeleteDuringMergeIsDropped() throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setSoftDeletesField("soft")
            .setMergePolicy(new LogDocMergePolicy())
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String id : new String[] {"a", "b"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      w.updateDocValues(new Term("id", "a"), new NumericDocValuesField("soft", 1));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        long[] seqNo = {0};
        w.getConfig()
            .setMergedSegmentWarmer(
                _ ->
                    seqNo[0] =
                        w.tryUpdateDocValue(
                            reader, 0, new NumericDocValuesField("soft", (Long) null)));
        w.forceMerge(1);
        int numDocs = w.getDocStats().numDocs;
        // javadoc: > 0 = un-delete applied, else -1; actual: > 0, then the merge drops "a"
        assertTrue(
            "expected seqNo == -1 or numDocs == 2 after the merge, actual seqNo="
                + seqNo[0]
                + " numDocs="
                + numDocs,
            seqNo[0] == -1 || numDocs == 2);
      }
    }
  }
}
