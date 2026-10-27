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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Uses only public API. A doc-values update is visible in an NRT reader, then the writer is
 * rolled back. Later writer sessions write a different update that gets the same doc-values
 * generation number and commit it. Reopening the old reader on that commit must show the
 * committed values (as a fresh DirectoryReader does), not the rolled-back ones.
 */
public class TestReopenAfterRollbackDocValuesGenReuse extends LuceneTestCase {

  private static String values(DirectoryReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : reader.leaves()) {
      NumericDocValues dv = ctx.reader().getNumericDocValues("n");
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        sb.append(dv != null && dv.advanceExact(doc) ? Long.toString(dv.longValue()) : "-");
        sb.append(' ');
      }
    }
    return sb.toString().trim();
  }

  public void testReopenOnLaterCommitShowsCommittedValues() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, new IndexWriterConfig(null));
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new NumericDocValuesField("n", i));
        w1.addDocument(doc);
      }
      w1.commit(); // one segment, values 0 1 2

      // session 1: an uncommitted update, seen by an NRT reader, then rolled back
      w1.updateNumericDocValue(new Term("id", "0"), "n", 100);
      DirectoryReader oldReader = DirectoryReader.open(w1);
      assertEquals("100 1 2", values(oldReader));
      w1.rollback();

      // session 2 changes nothing; opening it lets IndexFileDeleter remove the rolled-back files
      new IndexWriter(dir, new IndexWriterConfig(null)).close();

      // session 3: a different update; its doc-values generation number is free again
      try (IndexWriter w3 = new IndexWriter(dir, new IndexWriterConfig(null))) {
        w3.updateNumericDocValue(new Term("id", "1"), "n", 200);
        w3.commit();
      }

      List<IndexCommit> commits = DirectoryReader.listCommits(dir);
      IndexCommit last = commits.get(commits.size() - 1);
      try (DirectoryReader fresh = DirectoryReader.open(dir)) {
        assertEquals("0 200 2", values(fresh));
      }
      DirectoryReader reopened = DirectoryReader.openIfChanged(oldReader, last);
      assertNotNull(reopened);
      try {
        assertEquals("reopened on the last commit", "0 200 2", values(reopened));
      } finally {
        reopened.close();
        oldReader.close();
      }
    }
  }
}
