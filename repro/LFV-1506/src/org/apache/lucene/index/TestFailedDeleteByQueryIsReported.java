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
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFailedDeleteByQueryIsReported extends LuceneTestCase {

  public void testCommitThrowsTheFailureOfTheDeleteByQuery() throws Exception {
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (name.endsWith(".liv")) {
              throw new IOException("cannot create " + name);
            }
            return super.createOutput(name, context);
          }
        };
    try (dir;
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setReaderPooling(false))) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      w.deleteDocuments(new Term("id", "1"));
      w.deleteDocuments(new PhraseQuery("id", "1", "2"));
      // expected: the query's IllegalStateException (as 10.6.0); actual: only the .liv IOException
      expectThrows(IllegalStateException.class, w::commit);
    }
  }
}
