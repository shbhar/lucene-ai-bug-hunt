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
package repro;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;

/**
 * Natural-race check with public API only: a stock ConcurrentMergeScheduler, a plain Directory
 * implementation that models a slow disk which then fills up (the first file a merge creates
 * waits until released, then fails), no test points, no InfoStream hooks, and no Lucene test
 * framework. The natural merge started by a commit is still running when forceMerge(1) is called;
 * forceMerge adopts it; then the merge fails. Correct behaviour: forceMerge(1) throws. Counts how
 * often it returns normally instead.
 *
 * <p>Args: iterations [iwLogging=true|false]. iwLogging enables the IndexWriter info stream through
 * IndexWriterConfig.setInfoStream(PrintStream) to a discarding stream. Exit code 1 if any normal
 * return was seen.
 */
public class PublicApiForceMergeRace {

  /** Delegating Directory: merge outputs wait for the gate, then fail while armed. */
  static final class SlowThenFullDirectory extends Directory {
    final Directory in = new ByteBuffersDirectory();
    volatile CountDownLatch mergeStarted = new CountDownLatch(1);
    volatile CountDownLatch gate = new CountDownLatch(1);
    final AtomicBoolean armed = new AtomicBoolean();

    @Override
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
      if (context.context() == IOContext.Context.MERGE) {
        mergeStarted.countDown();
        try {
          gate.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          throw new IOException(e);
        }
        if (armed.get()) {
          throw new IOException("No space left on device (simulated) while writing " + name);
        }
      }
      return in.createOutput(name, context);
    }

    @Override
    public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
        throws IOException {
      return in.createTempOutput(prefix, suffix, context);
    }

    @Override
    public String[] listAll() throws IOException {
      return in.listAll();
    }

    @Override
    public void deleteFile(String name) throws IOException {
      in.deleteFile(name);
    }

    @Override
    public long fileLength(String name) throws IOException {
      return in.fileLength(name);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      in.sync(names);
    }

    @Override
    public void syncMetaData() throws IOException {
      in.syncMetaData();
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      in.rename(source, dest);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      return in.openInput(name, context);
    }

    @Override
    public Lock obtainLock(String name) throws IOException {
      return in.obtainLock(name);
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    @Override
    public void copyFrom(Directory from, String src, String dest, IOContext context)
        throws IOException {
      in.copyFrom(from, src, dest, context);
    }

    @Override
    public Set<String> getPendingDeletions() throws IOException {
      return in.getPendingDeletions();
    }
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  /** Returns 1 if forceMerge(1) returned normally, 0 if it threw. */
  static int once(boolean iwLogging) throws Exception {
    SlowThenFullDirectory dir = new SlowThenFullDirectory();
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergeScheduler(
                new ConcurrentMergeScheduler() {
                  @Override
                  protected void handleMergeException(Throwable exc) {
                    // the failure is expected; keep stderr quiet
                  }
                })
            .setMergePolicy(mp);
    if (iwLogging) {
      iwc.setInfoStream(new PrintStream(OutputStream.nullOutputStream()));
    }
    IndexWriter w = new IndexWriter(dir, iwc);
    w.addDocument(doc("0"));
    w.commit();
    w.addDocument(doc("1"));
    w.commit(); // registers and starts the natural merge [_0, _1]; it blocks on its first file
    if (dir.mergeStarted.await(30, TimeUnit.SECONDS) == false) {
      throw new AssertionError("natural merge did not start");
    }
    AtomicBoolean returnedNormally = new AtomicBoolean();
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                w.forceMerge(1);
                returnedNormally.set(true);
              } catch (Throwable e) {
                thrown.set(e);
              }
            });
    t.start();
    Thread.sleep(50); // let forceMerge flush, adopt the running merge and start waiting
    dir.armed.set(true);
    dir.gate.countDown(); // the disk is now full: the merge fails
    t.join(60_000);
    if (t.isAlive()) {
      throw new AssertionError("forceMerge did not finish");
    }
    for (int i = 0; i < 600 && w.isOpen(); i++) {
      Thread.sleep(10); // the tragic rollback closes the writer
    }
    w.close();
    dir.close();
    return returnedNormally.get() ? 1 : 0;
  }

  public static void main(String[] args) throws Exception {
    int iters = args.length > 0 ? Integer.parseInt(args[0]) : 200;
    boolean iwLogging = args.length > 1 && Boolean.parseBoolean(args[1]);
    int normal = 0;
    for (int i = 0; i < iters; i++) {
      normal += once(iwLogging);
    }
    System.out.println(
        "PUBLIC-API RACE iwLogging="
            + iwLogging
            + " iterations="
            + iters
            + " forceMerge(1) returned normally after its merge failed: "
            + normal);
    System.exit(normal > 0 ? 1 : 0);
  }
}
