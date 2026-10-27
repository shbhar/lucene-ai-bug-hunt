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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * When addIndexes(CodecReader...) fails after it queued its merges but before the merge scheduler
 * took all of them, the merges that never started stay queued. The next addIndexes(CodecReader...)
 * call runs them first, on the readers of the earlier, failed call.
 */
public class TestFailedAddIndexesLeavesQueuedMerges extends LuceneTestCase {

  /** One addIndexes merge per incoming reader, like TestAddIndexes' ConcurrentAddIndexesMergePolicy. */
  private static class OneMergePerReaderPolicy extends TieredMergePolicy {
    @Override
    public MergeSpecification findMerges(CodecReader... readers) {
      MergeSpecification spec = new MergeSpecification();
      for (CodecReader reader : readers) {
        spec.add(new OneMerge(reader));
      }
      return spec;
    }
  }

  /** A source index with one single-document segment per id. */
  private static Directory sourceIndex(String... ids) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String id : ids) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        w.addDocument(doc);
        w.flush();
      }
    }
    return dir;
  }

  private static CodecReader[] leaves(DirectoryReader reader) {
    List<CodecReader> leaves = new ArrayList<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      leaves.add((CodecReader) ctx.reader());
    }
    return leaves.toArray(new CodecReader[0]);
  }

  public void testNextAddIndexesRunsMergesOfFailedCall() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    final boolean[] armed = new boolean[1];
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(IndexWriter.class, "addIndexesReaderMerge")
                && callStackContains(MockDirectoryWrapper.class, "createOutput")) {
              armed[0] = false;
              throw new IOException("simulated disk full");
            }
          }
        });
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(new OneMergePerReaderPolicy());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    IndexWriter w = new IndexWriter(dir, iwc);

    Directory source1 = sourceIndex("a0", "a1"); // two segments -> two addIndexes merges
    Directory source2 = sourceIndex("b0");

    // 1. the first merge of this call fails; the call fails as a whole and adds nothing
    try (DirectoryReader reader1 = DirectoryReader.open(source1)) {
      armed[0] = true;
      expectThrows(IOException.class, () -> w.addIndexes(leaves(reader1)));
    }
    assertFalse("the failure was not injected", armed[0]);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());
    int docsAfterFailedCall;
    try (DirectoryReader nrt = DirectoryReader.open(w)) {
      docsAfterFailedCall = nrt.numDocs();
    }

    // 2. a later, unrelated addIndexes call with an open reader and no injected failure
    Throwable secondCallFailure = null;
    try (DirectoryReader reader2 = DirectoryReader.open(source2)) {
      w.addIndexes(leaves(reader2));
    } catch (Throwable t) {
      secondCallFailure = t;
    }
    int docsAfterSecondCall;
    try (DirectoryReader nrt = DirectoryReader.open(w)) {
      docsAfterSecondCall = nrt.numDocs();
    }
    w.rollback();
    dir.close();
    source1.close();
    source2.close();

    assertEquals("the failed addIndexes call added documents", 0, docsAfterFailedCall);
    if (secondCallFailure != null) {
      throw new AssertionError(
          "the second addIndexes call failed although its own input is fine", secondCallFailure);
    }
    assertEquals(1, docsAfterSecondCall);
  }

  /** Counts the addIndexes merges each call actually runs. */
  private static class CountingSerialMergeScheduler extends SerialMergeScheduler {
    int addIndexesMergesRun;

    @Override
    public synchronized void merge(MergeSource source, MergeTrigger trigger) throws IOException {
      if (trigger != MergeTrigger.ADD_INDEXES) {
        super.merge(source, trigger);
        return;
      }
      super.merge(
          new MergeSource() {
            @Override
            public MergePolicy.OneMerge getNextMerge() {
              MergePolicy.OneMerge merge = source.getNextMerge();
              if (merge != null) {
                addIndexesMergesRun++;
              }
              return merge;
            }

            @Override
            public void onMergeFinished(MergePolicy.OneMerge merge) {
              source.onMergeFinished(merge);
            }

            @Override
            public boolean hasPendingMerges() {
              return source.hasPendingMerges();
            }

            @Override
            public void merge(MergePolicy.OneMerge merge) throws IOException {
              source.merge(merge);
            }
          },
          trigger);
    }
  }

  /** Same, but the readers of the failed call stay open: the next call silently runs its merge. */
  public void testNextAddIndexesRunsMergeOfFailedCallOnOpenReaders() throws Exception {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    dir.setRandomIOExceptionRate(0.0);
    dir.setRandomIOExceptionRateOnOpen(0.0);
    final boolean[] armed = new boolean[1];
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(IndexWriter.class, "addIndexesReaderMerge")
                && callStackContains(MockDirectoryWrapper.class, "createOutput")) {
              armed[0] = false;
              throw new IOException("simulated disk full");
            }
          }
        });
    CountingSerialMergeScheduler scheduler = new CountingSerialMergeScheduler();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(new OneMergePerReaderPolicy());
    iwc.setMergeScheduler(scheduler);
    IndexWriter w = new IndexWriter(dir, iwc);
    Directory source1 = sourceIndex("a0", "a1");
    Directory source2 = sourceIndex("b0");
    DirectoryReader reader1 = DirectoryReader.open(source1);
    armed[0] = true;
    expectThrows(IOException.class, () -> w.addIndexes(leaves(reader1)));
    assertFalse("the failure was not injected", armed[0]);
    int mergesRunByFailedCall = scheduler.addIndexesMergesRun;

    scheduler.addIndexesMergesRun = 0;
    int docsAfterSecondCall;
    try (DirectoryReader reader2 = DirectoryReader.open(source2)) {
      w.addIndexes(leaves(reader2)); // one reader -> one merge
    }
    int mergesRunBySecondCall = scheduler.addIndexesMergesRun;
    try (DirectoryReader nrt = DirectoryReader.open(w)) {
      docsAfterSecondCall = nrt.numDocs();
    }
    w.rollback();
    reader1.close();
    dir.close();
    source1.close();
    source2.close();

    assertEquals(1, mergesRunByFailedCall);
    assertEquals(1, docsAfterSecondCall);
    assertEquals(
        "the second call must run only its own merge, not the leftover merge of the failed call",
        1,
        mergesRunBySecondCall);
  }
}
