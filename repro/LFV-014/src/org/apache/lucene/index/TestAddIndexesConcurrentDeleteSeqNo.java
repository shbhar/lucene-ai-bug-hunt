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

import java.util.concurrent.FutureTask;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestAddIndexesConcurrentDeleteSeqNo extends LuceneTestCase {

  public void testDeleteWithSmallerSeqNoDoesNotDeleteAddedDocument() throws Exception {
    try (Directory src = new ByteBuffersDirectory();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter srcWriter = new IndexWriter(src, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("id", "k", Field.Store.NO));
        srcWriter.addDocument(doc);
      }

      IndexWriter[] writer = new IndexWriter[1];
      FutureTask<Long> delete =
          new FutureTask<>(() -> writer[0].deleteDocuments(new Term("id", "k")));
      InfoStream runDeleteWhileAddIndexesCopies =
          new InfoStream() {
            @Override
            public void message(String component, String message) {
              if (message.startsWith("addIndexes: process directory")) {
                Thread other = new Thread(delete);
                other.start();
                try {
                  other.join();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }
            }

            @Override
            public boolean isEnabled(String component) {
              return component.equals("IW");
            }

            @Override
            public void close() {}
          };

      IndexWriterConfig iwc = new IndexWriterConfig().setInfoStream(runDeleteWhileAddIndexesCopies);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        writer[0] = w;
        long addIndexesSeqNo = w.addIndexes(src);
        long deleteSeqNo = delete.resultNow();
        assertTrue(deleteSeqNo + " < " + addIndexesSeqNo, deleteSeqNo < addIndexesSeqNo);
        try (DirectoryReader reader = DirectoryReader.open(w)) {
          // expected: the earlier delete leaves the added document live; actual: it deletes it
          String ops = "delete seqNo " + deleteSeqNo + ", addIndexes seqNo " + addIndexesSeqNo;
          assertEquals("live docs after " + ops, 1, reader.numDocs());
        }
      }
    }
  }
}
