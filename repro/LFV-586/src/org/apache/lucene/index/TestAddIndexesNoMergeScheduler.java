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

import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Since 9.4.0 (LUCENE-10216) addIndexes(CodecReader...) hands its merges to the configured
 * MergeScheduler and waits for them. With NoMergeScheduler, also together with NoMergePolicy as the
 * NoMergeScheduler javadoc recommends, the merge is never run, so addIndexes(CodecReader...) never
 * returns. NoMergePolicy keeps addIndexes(CodecReader...) working on purpose (its findMerges(CodecReader...)
 * comment); on 9.3.0 and earlier the same call returns normally.
 */
public class TestAddIndexesNoMergeScheduler extends LuceneTestCase {

  private static final long BOUND_MS = 10_000;

  private interface WriterCall {
    void run(IndexWriter w) throws Exception;
  }

  /** Runs the call in a thread; returns true if it finished within BOUND_MS. */
  private static boolean finishes(IndexWriter w, WriterCall call, String name) throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                call.run(w);
              } catch (Throwable e) {
                failure.set(e);
              }
            },
            name);
    t.start();
    t.join(BOUND_MS);
    boolean finished = t.isAlive() == false;
    if (finished == false) {
      // unblock the waiting thread (doWait turns the interrupt into ThreadInterruptedException)
      t.interrupt();
      t.join(BOUND_MS);
    }
    System.out.println(
        name + ": finished within " + BOUND_MS + " ms = " + finished + ", exception = " + failure.get());
    if (failure.get() != null) {
      StackTraceElement[] st = failure.get().getStackTrace();
      for (int i = 0; i < Math.min(6, st.length); i++) {
        System.out.println("    at " + st[i]);
      }
    }
    return finished;
  }

  private IndexWriter newWriter(Directory dir, MergePolicy mp) throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random()))
            .setMergeScheduler(NoMergeScheduler.INSTANCE)
            .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMBufferSizeMB(64);
    if (mp != null) {
      iwc.setMergePolicy(mp); // else: the default merge policy
    }
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 15; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      w.addDocument(doc);
      w.commit(); // one small segment per commit; the commit's maybeMerge asks the policy
    }
    return w;
  }

  /**
   * addIndexes(CodecReader...) with NoMergeScheduler and NoMergePolicy, the pair the
   * NoMergeScheduler javadoc recommends: the addIndexes merge is handed to the merge scheduler
   * (since LUCENE-10216) and awaited.
   */
  public void testAddIndexesCodecReaderWithNoMergePolicy() throws Exception {
    try (Directory src = newDirectory();
        Directory dir = newDirectory()) {
      try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new StringField("id", "x", Field.Store.YES));
        sw.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(src)) {
        IndexWriter w = newWriter(dir, NoMergePolicy.INSTANCE);
        CodecReader[] leaves =
            r.leaves().stream()
                .map(ctx -> (CodecReader) ctx.reader())
                .toArray(CodecReader[]::new);
        boolean done = finishes(w, x -> x.addIndexes(leaves), "addIndexes(CodecReader...)");
        int numDocs = done ? w.getDocStats().numDocs : -1;
        System.out.println("documents in the writer after the call: " + numDocs + " (15 before)");
        w.rollback();
        // The minimal contract: the call returns or throws; it must not block forever.
        assertTrue(
            "IndexWriter.addIndexes(CodecReader...) did not return with NoMergeScheduler", done);
      }
    }
  }

  /** Control: the same call with SerialMergeScheduler returns and adds the document. */
  public void testControlAddIndexesWithSerialMergeScheduler() throws Exception {
    try (Directory src = newDirectory();
        Directory dir = newDirectory()) {
      try (IndexWriter sw = new IndexWriter(src, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new StringField("id", "x", Field.Store.YES));
        sw.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(src)) {
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig(new MockAnalyzer(random()))
                    .setMergeScheduler(new SerialMergeScheduler())
                    .setMergePolicy(NoMergePolicy.INSTANCE));
        CodecReader[] leaves =
            r.leaves().stream()
                .map(ctx -> (CodecReader) ctx.reader())
                .toArray(CodecReader[]::new);
        boolean done = finishes(w, x -> x.addIndexes(leaves), "addIndexes (SerialMergeScheduler)");
        assertTrue(done);
        assertEquals(1, w.getDocStats().numDocs);
        w.close();
      }
    }
  }
}
