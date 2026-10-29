package org.apache.lucene.util;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.OfflineSorter.BufferSize;
import org.apache.lucene.util.OfflineSorter.ByteSequencesWriter;

public class TestOfflineSorterFailureCleanup extends LuceneTestCase {

  public void testFailedSortLeavesNoTempFiles() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      String input;
      try (IndexOutput out = dir.createTempOutput("input", "tmp", IOContext.DEFAULT)) {
        ByteSequencesWriter writer = new ByteSequencesWriter(out);
        for (int i = 0; i < 1100; i++) {
          writer.write(new byte[1000]);
        }
        out.writeShort(Short.MAX_VALUE);
        CodecUtil.writeFooter(out);
        input = out.getName();
      }
      DeferredExecutor executor = new DeferredExecutor();
      OfflineSorter sorter =
          new OfflineSorter(
              dir,
              "foo",
              OfflineSorter.DEFAULT_COMPARATOR,
              BufferSize.megabytes(1),
              OfflineSorter.MAX_TEMPFILES,
              -1,
              executor,
              2);
      expectThrows(EOFException.class, () -> sorter.sort(input));
      executor.runQueued();
      // expected: only the input file is left; actual: also foo_sort_1.tmp, written by the partition task sort() had submitted
      assertEquals(List.of(input), List.of(dir.listAll()));
    }
  }

  private static final class DeferredExecutor extends AbstractExecutorService {
    private final List<Runnable> queued = new ArrayList<>();

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> task) {
      return new FutureTask<>(task) {
        @Override
        public T get() throws InterruptedException, ExecutionException {
          run();
          return super.get();
        }
      };
    }

    @Override
    public void execute(Runnable task) {
      queued.add(task);
    }

    void runQueued() {
      queued.forEach(Runnable::run);
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }
}
