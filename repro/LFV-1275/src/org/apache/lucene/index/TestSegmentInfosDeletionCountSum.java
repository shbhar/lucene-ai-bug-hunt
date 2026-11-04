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
import org.apache.lucene.util.StringHelper;

public class TestSegmentInfosDeletionCountSum extends LuceneTestCase {

  public void testCommitRejectsDeletionCountSumAboveMaxDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (String id : new String[] {"0", "1"}) {
          Document doc = new Document();
          doc.add(new StringField("id", id, Field.Store.NO));
          w.addDocument(doc);
        }
        w.commit();
        w.deleteDocuments(new Term("id", "0"));
      }
      SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
      SegmentCommitInfo sci = sis.info(0);
      sis.clear();
      sis.add(
          new SegmentCommitInfo(
              sci.info, sci.getDelCount(), 2, sci.getDelGen(), sci.getFieldInfosGen(),
              sci.getDocValuesGen(), StringHelper.randomId()));
      // Expected: commit refuses delCount 1 + softDelCount 2 > maxDoc 2, or the commit reads back; actual: CorruptIndexException on read.
      try {
        sis.commit(dir);
      } catch (IllegalStateException expected) {
        return;
      }
      SegmentInfos.readLatestCommit(dir);
    }
  }
}
