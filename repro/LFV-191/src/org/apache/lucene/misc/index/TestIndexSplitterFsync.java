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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.mockfile.FilterFileChannel;
import org.apache.lucene.tests.mockfile.FilterFileSystemProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

@LuceneTestCase.SuppressFileSystems("*")
public class TestIndexSplitterFsync extends LuceneTestCase {

  public void testSplitFsyncsCopiedFiles() throws Exception {
    Path src = createTempDir("src");
    try (Directory dir = FSDirectory.open(src);
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document());
    }
    Path dest = createTempDir("dest");
    Set<String> fsynced = new HashSet<>();
    FilterFileSystemProvider fs =
        new FilterFileSystemProvider("fsynctracking://", dest.getFileSystem()) {
          @Override
          public FileChannel newFileChannel(
              Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
              throws IOException {
            return new FilterFileChannel(super.newFileChannel(path, options, attrs)) {
              @Override
              public void force(boolean metaData) throws IOException {
                super.force(metaData);
                fsynced.add(path.getFileName().toString());
              }
            };
          }
        };

    new IndexSplitter(src).split(fs.wrapPath(dest), new String[] {"_0"});

    Set<String> notFsynced;
    try (Directory dir = FSDirectory.open(dest)) {
      notFsynced = new TreeSet<>(SegmentInfos.readLatestCommit(dir).files(false));
    }
    notFsynced.removeAll(fsynced);
    // expected: the split's commit references only fsynced files; actual: the copied files are not
    assertTrue(
        "segment files of the split's commit never fsynced: " + notFsynced, notFsynced.isEmpty());
  }
}
