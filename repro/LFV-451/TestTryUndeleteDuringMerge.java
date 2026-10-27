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
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOUtils;

/**
 * Un-deletes a soft-deleted document with tryUpdateDocValue (soft-deletes value = null) while a
 * merge of its segment is running, from the merged-segment warmer as in
 * TestSoftDeletesRetentionMergePolicy#testDeleteDocWhileMergeThatIsSoftDeleted. Per the javadoc the
 * call either fails with -1, or it succeeds and the un-delete must stay in the index.
 */
public class TestTryUndeleteDuringMerge extends LuceneTestCase {

  private static final String SOFT = "soft_delete";

  /** No retention: the merge leaves the soft-deleted document out. */
  public void testUndeleteDuringMerge() throws IOException {
    runUndeleteDuringMerge(new LogDocMergePolicy());
  }

  /** A retention query that does not match the document: the merge leaves it out too. */
  public void testUndeleteDuringMergeWithRetentionPolicy() throws IOException {
    runUndeleteDuringMerge(
        new SoftDeletesRetentionMergePolicy(SOFT, MatchNoDocsQuery::new, new LogDocMergePolicy()));
  }

  /** Control: a retention query that keeps the document, so the merge carries the un-delete. */
  public void testControlRetainAll() throws IOException {
    runUndeleteDuringMerge(
        new SoftDeletesRetentionMergePolicy(SOFT, MatchAllDocsQuery::new, new LogDocMergePolicy()));
  }

  private static void addDoc(IndexWriter writer, String id) throws IOException {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    writer.addDocument(d);
  }

  private void runUndeleteDuringMerge(MergePolicy mergePolicy) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig config = newIndexWriterConfig().setSoftDeletesField(SOFT);
    config.setReaderPooling(true);
    config.setMergePolicy(mergePolicy);
    IndexWriter writer = new IndexWriter(dir, config);
    addDoc(writer, "0");
    addDoc(writer, "1");
    writer.flush();
    addDoc(writer, "2");
    writer.flush();
    writer.updateDocValues(new Term("id", "0"), new NumericDocValuesField(SOFT, 1)); // soft-delete
    DirectoryReader reader = DirectoryReader.open(writer);
    assertEquals(2, reader.numDocs());
    assertEquals("0", reader.storedFields().document(0).get("id"));

    AtomicLong seqNo = new AtomicLong();
    AtomicLong numDocsDuringMerge = new AtomicLong(-1);
    writer
        .getConfig()
        .setMergedSegmentWarmer(
            mergedReader -> {
              if (seqNo.get() == 0) {
                // un-delete doc "0" while forceMerge(1) is running
                seqNo.set(
                    writer.tryUpdateDocValue(
                        reader, 0, new NumericDocValuesField(SOFT, (Long) null)));
                try (DirectoryReader during = DirectoryReader.open(writer)) {
                  numDocsDuringMerge.set(during.numDocs());
                }
              }
            });
    writer.forceMerge(1);
    long commitSeqNo = writer.commit();
    int hits;
    try (DirectoryReader committed =
        new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT)) {
      hits = new IndexSearcher(committed).count(new TermQuery(new Term("id", "0")));
    }
    IOUtils.close(reader, writer, dir);

    assertTrue("the warmer did not run", seqNo.get() != 0);
    if (seqNo.get() != -1) {
      assertEquals(
          "tryUpdateDocValue returned "
              + seqNo.get()
              + " and a reader opened during the merge had numDocs="
              + numDocsDuringMerge.get()
              + ", but the commit (seqNo "
              + commitSeqNo
              + ") does not contain the un-deleted document",
          1,
          hits);
    }
  }
}
