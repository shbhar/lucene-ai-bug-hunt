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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Constants;

/**
 * With an index sort, a flush first writes stored fields (and term vectors) to temporary files,
 * then SortingStoredFieldsConsumer.flush / SortingTermVectorsConsumer.flush open a reader on the
 * temporary files and create the final, sorted writer. If creating the sorted writer fails (for
 * example an IOException from Directory#createOutput: disk full, too many open files), the flush
 * fails and IndexWriter closes itself, as it should, but the reader on the temporary files is
 * never closed: it is opened before the try block whose finally closes it.
 */
public class TestSortingFlushTempReaderLeak extends LuceneTestCase {

  private static IndexWriterConfig config() {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setMaxBufferedDocs(1000);
    return iwc;
  }

  private static void addDocs(IndexWriter w, boolean termVectors) throws IOException {
    FieldType tv = new FieldType(TextField.TYPE_NOT_STORED);
    tv.setStoreTermVectors(true);
    tv.freeze();
    for (int i = 0; i < 3; i++) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("sort", 10 - i)); // reverse order, so the flush sorts
      doc.add(new StoredField("stored", "value" + i));
      if (termVectors) {
        doc.add(new Field("body", "some text " + i, tv));
      }
      w.addDocument(doc);
    }
  }

  /** Fails once, on the first createOutput made from inside {@code consumer.flush}. */
  private static MockDirectoryWrapper.Failure failOnCreateOutputIn(Class<?> consumer) {
    return new MockDirectoryWrapper.Failure() {
      @Override
      public void eval(MockDirectoryWrapper dir) throws IOException {
        if (doFail
            && callStackContains(consumer, "flush")
            && callStackContains(MockDirectoryWrapper.class, "createOutput")) {
          doFail = false;
          throw new IOException("simulated failure creating the sorted output");
        }
      }
    };
  }

  private void assertNoHandleLeftOpen(Class<?> consumer, boolean termVectors) throws IOException {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    IndexWriter w = new IndexWriter(dir, config());
    addDocs(w, termVectors);
    MockDirectoryWrapper.Failure failure = failOnCreateOutputIn(consumer);
    dir.failOn(failure);
    failure.setDoFail();
    IOException e = expectThrows(IOException.class, w::commit);
    assertEquals("simulated failure creating the sorted output", e.getMessage());
    // the flush failure is tragic: the writer closed itself and must have released its files
    assertNotNull(w.getTragicException());
    assertFalse(w.isOpen());
    w.close();
    long open = dir.getFileHandleCount();
    if (open != 0) {
      // MockDirectoryWrapper#close names the files that are still open
      RuntimeException leak = expectThrows(RuntimeException.class, dir::close);
      fail(
          "IndexWriter closed itself after the failed flush but left "
              + open
              + " file handle(s) open: "
              + leak.getMessage());
    }
    dir.close();
  }

  public void testStoredFieldsTempReaderClosedWhenSortedWriterCannotBeCreated()
      throws IOException {
    assertNoHandleLeftOpen(SortingStoredFieldsConsumer.class, false);
  }

  public void testTermVectorsTempReaderClosedWhenSortedWriterCannotBeCreated()
      throws IOException {
    assertNoHandleLeftOpen(SortingTermVectorsConsumer.class, true);
  }

  /** Same scenario on a real file system directory: the OS file descriptors stay open. */
  public void testFileDescriptorsOnFSDirectory() throws IOException {
    assumeTrue("needs /proc/self/fd", Constants.LINUX && Files.isDirectory(Path.of("/proc/self/fd")));
    Path path = createTempDir("sorted-flush-leak");
    final boolean[] armed = {false};
    Directory dir =
        new FilterDirectory(new NIOFSDirectory(path)) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            if (armed[0] && callStackContains(SortingStoredFieldsConsumer.class, "flush")) {
              armed[0] = false;
              throw new IOException("simulated failure creating the sorted output");
            }
            return super.createOutput(name, context);
          }
        };
    IndexWriter w = new IndexWriter(dir, config());
    addDocs(w, false);
    armed[0] = true;
    expectThrows(IOException.class, w::commit);
    assertNotNull(w.getTragicException());
    w.close();
    List<String> stillOpen = openFilesUnder(path);
    dir.close();
    assertEquals(
        "file descriptors still open under the index directory after IndexWriter closed itself",
        List.of(),
        stillOpen);
  }

  private static List<String> openFilesUnder(Path dir) throws IOException {
    List<String> open = new ArrayList<>();
    String prefix = dir.toRealPath().toString();
    try (Stream<Path> fds = Files.list(Path.of("/proc/self/fd"))) {
      for (Path fd : (Iterable<Path>) fds::iterator) {
        try {
          String target = Files.readSymbolicLink(fd).toString();
          if (target.startsWith(prefix)) {
            open.add(target.substring(prefix.length() + 1));
          }
        } catch (IOException ignored) {
          // the fd was closed while listing
        }
      }
    }
    return open;
  }
}
