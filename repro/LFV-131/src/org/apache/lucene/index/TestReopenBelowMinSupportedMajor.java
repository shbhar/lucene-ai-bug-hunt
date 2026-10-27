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
 * A reader opened through the expert {@link DirectoryReader#open(IndexCommit, int,
 * java.util.Comparator)} path (minSupportedMajorVersion below {@link Version#MIN_SUPPORTED_MAJOR})
 * cannot be checked for changes or reopened: {@code isCurrent()} and {@code openIfChanged(reader)}
 * re-read the commit with the default minimum and throw {@link IndexFormatTooOldException}, although
 * the index has not changed.
 */
public class TestReopenBelowMinSupportedMajor extends LuceneTestCase {

  /**
   * Builds the state an index created with Lucene 8.x has after Lucene 9.x upgraded it
   * (IndexUpgrader / forceMerge in 9.x): segments written by 9.x whose minVersion is 8.x, and an
   * index created major of 8. The segment files are written by the current codec; only the
   * versions recorded in the .si and segments_N files are set, through public API.
   */
  private static void writeIndexDerivedFromOlderMajor(Directory dir) throws IOException {
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.YES));
      w.addDocument(doc);
      w.commit();
    }
    int createdMajor = Version.MIN_SUPPORTED_MAJOR - 1;
    Version segmentVersion = Version.fromBits(Version.MIN_SUPPORTED_MAJOR, 12, 3);
    Version segmentMinVersion = Version.fromBits(Version.MIN_SUPPORTED_MAJOR - 1, 11, 4);
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

  private static DirectoryReader openWithLowerMinimum(Directory dir) throws IOException {
    // the default open refuses this index, as documented
    expectThrows(IndexFormatTooOldException.class, () -> DirectoryReader.open(dir));
    IndexCommit commit = DirectoryReader.listCommits(dir).getLast();
    DirectoryReader reader = DirectoryReader.open(commit, Version.MIN_SUPPORTED_MAJOR - 1, null);
    assertEquals(1, reader.numDocs());
    return reader;
  }

  public void testIsCurrentOnUnchangedIndex() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      writeIndexDerivedFromOlderMajor(dir);
      try (DirectoryReader reader = openWithLowerMinimum(dir)) {
        // nothing was committed after the reader was opened
        assertTrue(reader.isCurrent());
      }
    }
  }

  public void testOpenIfChangedOnUnchangedIndex() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      writeIndexDerivedFromOlderMajor(dir);
      try (DirectoryReader reader = openWithLowerMinimum(dir)) {
        // "If the index has changed since the provided reader was opened, open and return a new
        // reader; else, return null."
        assertNull(DirectoryReader.openIfChanged(reader));
      }
    }
  }
}
