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
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestNewerDocValuesUpdateSurvivesMerge extends LuceneTestCase {

  public void testUpdateWithHigherSequenceNumberSurvivesMerge() throws Exception {
    CountDownLatch olderUpdateParked = new CountDownLatch(1);
    CountDownLatch releaseOlderUpdate = new CountDownLatch(1);
    CountDownLatch mergeParked = new CountDownLatch(1);
    CountDownLatch releaseMerge = new CountDownLatch(1);
    AtomicReference<MergeScheduler.MergeSource> mergeSource = new AtomicReference<>();

    Query slowDelete =
        new MatchNoDocsQuery("slow") {
          @Override
          public Query rewrite(IndexSearcher searcher) throws IOException {
            olderUpdateParked.countDown();
            await(releaseOlderUpdate);
            return super.rewrite(searcher);
          }
        };
    MergeScheduler heldMerge =
        new MergeScheduler() {
          @Override
          public void merge(MergeSource source, MergeTrigger trigger) {
            mergeSource.set(source);
          }

          @Override
          public Directory wrapForMerge(MergePolicy.OneMerge merge, Directory in) {
            mergeParked.countDown();
            await(releaseMerge);
            return in;
          }

          @Override
          public void close() {}
        };

    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setCodec(TestUtil.getDefaultCodec())
                .setMergeScheduler(heldMerge));
    Document doc = new Document();
    doc.add(new StringField("id", "a", Field.Store.NO));
    doc.add(new NumericDocValuesField("n", 0));
    w.addDocument(doc);
    w.flush();
    w.addDocument(new Document());
    w.flush();
    w.forceMerge(1, false);
    MergePolicy.OneMerge merge = mergeSource.get().getNextMerge();

    AtomicLong olderSeqNo = new AtomicLong();
    Thread older =
        new Thread(
            () -> {
              try {
                w.deleteDocuments(slowDelete);
                olderSeqNo.set(w.updateNumericDocValue(new Term("id", "a"), "n", 1));
                w.addDocument(new Document());
                w.flushNextBuffer();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    older.start();
    await(olderUpdateParked);

    long newerSeqNo = w.updateNumericDocValue(new Term("id", "a"), "n", 2);
    w.addDocument(new Document());
    w.flushNextBuffer();
    assertTrue(olderSeqNo.get() < newerSeqNo);

    Thread merger =
        new Thread(
            () -> {
              try {
                mergeSource.get().merge(merge);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    merger.start();
    await(mergeParked);

    releaseOlderUpdate.countDown();
    older.join();
    w.commit();
    long beforeMergeFinished = committedValue(dir);

    releaseMerge.countDown();
    merger.join();
    w.commit();
    long afterMergeFinished = committedValue(dir);

    w.rollback();
    dir.close();
    // expected: 2 (the later update) in both commits; actual: 2 before the merge finished, 1 after
    assertEquals(2, beforeMergeFinished);
    assertEquals(2, afterMergeFinished);
  }

  private static long committedValue(Directory dir) throws IOException {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      NumericDocValues values = MultiDocValues.getNumericValues(reader, "n");
      values.nextDoc();
      return values.longValue();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue("timed out", latch.await(30, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }
}
