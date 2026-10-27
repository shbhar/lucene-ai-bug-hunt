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
 * The javadocs of {@link SegmentInfos#readLatestCommit(Directory, int)} and {@link
 * SegmentInfos#readCommit(Directory, String, int)} do not describe the check the code performs
 * since GITHUB#14607. readLatestCommit says the commit is loaded only if its {@link
 * SegmentInfos#getIndexCreatedVersionMajor()} is strictly greater than the minimum; readCommit says
 * each segment's minVersion must be strictly greater than the minimum. The code ignores the created
 * major and accepts a segment whose minVersion major equals the minimum. Each test asserts the
 * documented behaviour, so each failure is a javadoc/code mismatch.
 */
public class TestSegmentInfosMinMajorJavadoc extends LuceneTestCase {

  /**
   * One-segment index whose .si and segments_N record the given created major and segment
   * minVersion (segment version = minVersion). The segment files are written by the current codec;
   * only the recorded versions are set, through public API.
   */
  private static void writeIndex(Directory dir, int createdMajor, Version segmentMinVersion)
      throws IOException {
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.YES));
      w.addDocument(doc);
      w.commit();
    }
    Version segmentVersion = segmentMinVersion;
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

  /** readLatestCommit javadoc: created major must be strictly greater than the minimum. */
  public void testReadLatestCommitCreatedMajorNotGreater() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      int min = Version.MIN_SUPPORTED_MAJOR;
      writeIndex(dir, min - 1, Version.fromBits(min, 0, 0)); // created major below the minimum
      expectThrows(IndexFormatTooOldException.class, () -> SegmentInfos.readLatestCommit(dir, min));
    }
  }

  /** readCommit javadoc: each segment's minVersion must be strictly greater than the minimum. */
  public void testReadCommitMinVersionEqualToMinimum() throws IOException {
    try (Directory dir = newFSDirectory(createTempDir())) {
      int min = Version.MIN_SUPPORTED_MAJOR;
      writeIndex(dir, min, Version.fromBits(min, 0, 0)); // minVersion major == minimum
      String segmentsFile = SegmentInfos.getLastCommitSegmentsFileName(dir);
      expectThrows(
          IndexFormatTooOldException.class, () -> SegmentInfos.readCommit(dir, segmentsFile, min));
    }
  }
}
