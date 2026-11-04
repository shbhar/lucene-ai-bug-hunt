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
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestKeepLastNAfterPreparedRollback extends LuceneTestCase {

  public void testKeepLastTwoAfterPreparedRollback() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 =
          new IndexWriter(
              dir, new IndexWriterConfig().setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE));
      w1.addDocument(new Document());
      w1.commit();
      w1.addDocument(new Document());
      w1.commit();
      try (DirectoryReader nrt = DirectoryReader.open(w1)) {
        w1.addDocument(new Document());
        w1.prepareCommit();
        w1.rollback();
        new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setIndexCommit(nrt.getIndexCommit())
                    .setIndexDeletionPolicy(new KeepLastNCommitsDeletionPolicy(2)))
            .rollback();
      }
      // expected: both commits kept by KeepLastNCommitsDeletionPolicy(2); actual: segments_1 deleted
      assertEquals(
          List.of(1L, 2L),
          DirectoryReader.listCommits(dir).stream().map(IndexCommit::getGeneration).toList());
    }
  }
}
