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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNrtOpenKeepCheckFailureLeaksReader extends LuceneTestCase {

  private static IndexWriterConfig config(Supplier<Query> retention) {
    return new IndexWriterConfig()
        .setSoftDeletesField("soft")
        .setMergePolicy(
            new SoftDeletesRetentionMergePolicy("soft", retention, NoMergePolicy.INSTANCE));
  }

  public void testFailedKeepCheckOnFirstNrtOpen() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    AtomicBoolean fail = new AtomicBoolean();
    Supplier<Query> retention =
        () -> {
          if (fail.getAndSet(false)) {
            throw new IllegalStateException("retention query unavailable");
          }
          return new MatchAllDocsQuery();
        };
    try (IndexWriter w = new IndexWriter(dir, config(retention))) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.NO));
      w.addDocument(doc);
      w.flush();
      w.softUpdateDocument(new Term("id", "1"), doc, new NumericDocValuesField("soft", 1));
    }

    IndexWriter w = new IndexWriter(dir, config(retention));
    fail.set(true);
    expectThrows(IllegalStateException.class, () -> DirectoryReader.open(w));
    w.close();
    // expected no open files after the failed open and close(); actual: _0's files are still open
    dir.close();
  }
}
