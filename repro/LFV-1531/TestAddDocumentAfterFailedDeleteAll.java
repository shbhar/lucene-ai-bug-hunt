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
import org.apache.lucene.util.InfoStream;

public class TestAddDocumentAfterFailedDeleteAll extends LuceneTestCase {

  public void testAddDocumentAfterFailedDeleteAll() throws Exception {
    boolean[] failOnce = new boolean[1];
    InfoStream infoStream =
        new InfoStream() {
          @Override
          public void message(String component, String message) {}

          @Override
          public boolean isEnabled(String component) {
            if (failOnce[0] && component.equals("DWFC")) {
              failOnce[0] = false;
              throw new IllegalStateException("logging failed");
            }
            return false;
          }

          @Override
          public void close() {}
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setInfoStream(infoStream))) {
      w.addDocument(new Document());
      failOnce[0] = true;
      expectThrows(IllegalStateException.class, w::deleteAll);
      Document doc = new Document();
      doc.add(new StringField("id", "2", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      // expected: the document added and committed after the failed deleteAll() is in the index;
      // actual: it is missing
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.docFreq(new Term("id", "2")));
      }
    }
  }
}
