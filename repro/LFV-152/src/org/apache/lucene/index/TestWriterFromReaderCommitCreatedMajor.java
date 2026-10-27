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
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Version;

/**
 * Since the per-segment version check (GITHUB#14607), an index whose created major is below {@link
 * Version#MIN_SUPPORTED_MAJOR} but whose every segment is at least {@link
 * Version#MIN_SUPPORTED_MAJOR} opens normally, and an IndexWriter opens on it in APPEND mode or from
 * a {@link DirectoryReader#listCommits} commit. Opening the IndexWriter from the same commit obtained
 * through {@link DirectoryReader#getIndexCommit()} is rejected, because that path still checks the
 * index created major.
 */
public class TestWriterFromReaderCommitCreatedMajor extends LuceneTestCase {

  /**
   * Builds the state an index created with Lucene 8.x has after Lucene 9.x re-indexed it in place
   * (IndexWriter.deleteAll() and new documents in 9.x): every segment was written by 9.x and holds
   * only 9.x documents, while the index created major stays 8. The segment files are written by the
   * current codec; only the versions recorded in the .si and segments_N files are set, through
   * public API.
   */
  private static void writeIndexReindexedByMinSupportedMajor(Directory dir) throws IOException {
    writeIndex(dir, Version.fromBits(Version.MIN_SUPPORTED_MAJOR, 12, 3));
  }

  private static void writeIndex(Directory dir, Version segmentMinVersion) throws IOException {
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.YES));
      w.addDocument(doc);
      w.commit();
    }
    int createdMajor = Version.MIN_SUPPORTED_MAJOR - 1;
    Version segmentVersion = Version.fromBits(Version.MIN_SUPPORTED_MAJOR, 12, 3);
    SegmentInfos old = SegmentInfos.readLatestCommit(dir);
    SegmentInfos infos = new SegmentInfos(createdMajor);
    for (SegmentCommitInfo sci : old) {
      SegmentInfo si = sci.info;
      SegmentInfo rewritten =
          new SegmentInfo(
              dir,
              segmentVersion,
              segmentMinVersion,
              si.name,
              si.maxDoc(),
              si.getUseCompoundFile(),
              si.getHasBlocks(),
              si.getCodec(),
              si.getDiagnostics(),
              si.getId(),
              si.getAttributes(),
              si.getIndexSort());
      String siFile = IndexFileNames.segmentFileName(si.name, "", "si");
      Set<String> files = new HashSet<>(si.files());
      files.remove(siFile);
      rewritten.setFiles(files);
      dir.deleteFile(siFile);
      si.getCodec().segmentInfoFormat().write(dir, rewritten, IOContext.DEFAULT);
      dir.sync(Set.of(siFile));
      infos.add(
          new SegmentCommitInfo(
              rewritten,
              sci.getDelCount(),
              sci.getSoftDelCount(),
              sci.getDelGen(),
              sci.getFieldInfosGen(),
              sci.getDocValuesGen(),
              sci.getId()));
    }
    infos.updateGenerationVersionAndCounter(old);
    infos.commit(dir);
    dir.deleteFile(old.getSegmentsFileName());
  }

  public void testWriterFromReaderCommit() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      writeIndexReindexedByMinSupportedMajor(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // the default open accepts the index: every segment is >= MIN_SUPPORTED_MAJOR
        assertEquals(1, reader.numDocs());
        // the same commit, as a listCommits() commit, opens an IndexWriter
        IndexCommit listed = DirectoryReader.listCommits(dir).getLast();
        assertEquals(listed.getSegmentsFileName(), reader.getIndexCommit().getSegmentsFileName());
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexCommit(listed))) {
          w.rollback();
        }
        // ... but not as the reader's own commit (IndexWriterConfig#setIndexCommit javadoc: "This can
        // also be used to open IndexWriter from a near-real-time reader, if you pass the reader's
        // DirectoryReader#getIndexCommit")
        try (IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setIndexCommit(reader.getIndexCommit()))) {
          w.addDocument(new Document());
          w.commit();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.numDocs());
      }
    }
  }

  /**
   * Control (passes today and must keep passing): an IndexWriter must still refuse the commit of a
   * reader that was opened below MIN_SUPPORTED_MAJOR, i.e. segments that hold older documents.
   */
  public void testWriterFromReaderOpenedBelowMinimumIsRejected() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      writeIndex(dir, Version.fromBits(Version.MIN_SUPPORTED_MAJOR - 1, 11, 4));
      IndexCommit listed = DirectoryReader.listCommits(dir).getLast();
      try (DirectoryReader reader =
          DirectoryReader.open(listed, Version.MIN_SUPPORTED_MAJOR - 1, null)) {
        expectThrows(
            IllegalArgumentException.class,
            () -> new IndexWriter(dir, new IndexWriterConfig().setIndexCommit(reader.getIndexCommit())));
      }
    }
  }
}
