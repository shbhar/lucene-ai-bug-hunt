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
package org.apache.lucene.misc.index;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.mockfile.FilterFileChannel;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexSplitter.split publishes a new commit (segments_N, fsynced, directory fsynced) in the
 * destination whose segment files it copied with Files.copy and whose .si it rewrote, but it never
 * fsyncs those files. File data is durable only after fsync, so a machine crash after split()
 * returns can leave a destination whose newest commit references empty files.
 */
@LuceneTestCase.SuppressFileSystems("*")
public class TestIndexSplitterNotDurable extends LuceneTestCase {

  /** Records the names of files whose FileChannel was forced (fsync). */
  static final class SyncTrackingFS extends FilterFileSystemProvider {
    final Set<String> forced = ConcurrentHashMap.newKeySet();

    SyncTrackingFS(FileSystem delegate) {
      super("synctracking://", delegate);
    }

    @Override
    public FileChannel newFileChannel(
        Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
        throws IOException {
      final String name = path.getFileName().toString();
      return new FilterFileChannel(super.newFileChannel(path, options, attrs)) {
        @Override
        public void force(boolean metaData) throws IOException {
          super.force(metaData);
          forced.add(name);
        }
      };
    }

    @Override
    public void move(Path source, Path target, java.nio.file.CopyOption... options)
        throws IOException {
      super.move(source, target, options);
      // a rename keeps the inode: data fsynced under the old name stays durable
      if (forced.remove(source.getFileName().toString())) {
        forced.add(target.getFileName().toString());
      }
    }
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  public void testSplitResultIsDurable() throws Exception {
    Path src = createTempDir("src");
    try (Directory dir = FSDirectory.open(src);
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(doc("a"));
      w.commit(); // segment _0
      w.addDocument(doc("b"));
      w.commit(); // segment _1
    }
    Path destRaw = createTempDir("dest");
    SyncTrackingFS fs = new SyncTrackingFS(destRaw.getFileSystem());
    Path dest = fs.wrapPath(destRaw);

    new IndexSplitter(src).split(dest, new String[] {"_1"});

    // Every file the destination's commit references must have been fsynced.
    Set<String> referenced;
    try (Directory d = FSDirectory.open(destRaw)) {
      referenced = new TreeSet<>(SegmentInfos.readLatestCommit(d).files(true));
    }
    Set<String> notSynced = new TreeSet<>(referenced);
    notSynced.removeAll(fs.forced);

    // Crash state: names are durable (the commit fsynced the directory), file data only if fsynced.
    Path crashed = createTempDir("crashed");
    for (String f : referenced) {
      Path target = crashed.resolve(f);
      if (fs.forced.contains(f)) {
        Files.copy(destRaw.resolve(f), target);
      } else {
        Files.createFile(target); // its bytes were never fsynced
      }
    }
    String openResult;
    try (Directory d = FSDirectory.open(crashed);
        DirectoryReader r = DirectoryReader.open(d)) {
      openResult = "opened, numDocs=" + r.numDocs();
    } catch (IOException e) {
      openResult = "fails: " + e;
    }
    assertTrue(
        "files referenced by the split's commit that were never fsynced: "
            + notSynced
            + " (referenced="
            + List.copyOf(referenced)
            + "); crash state open "
            + openResult,
        notSynced.isEmpty());
  }
}
