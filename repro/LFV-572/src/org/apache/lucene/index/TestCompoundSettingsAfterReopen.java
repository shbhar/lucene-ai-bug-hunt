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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Compound-file merge settings configured as MIGRATE.md describes ({@code
 * iwc.getCodec().compoundFormat().setShouldUseCompoundFile(false)}) on the writer's codec are not
 * used for segments read from disk: MergePolicy#isMerged asks the segment's own codec, which for a
 * segment read from a commit is the codec looked up by name, with default settings. A forceMerge(1)
 * of an already merged index then rewrites the whole segment after every writer reopen.
 *
 * <p>Public API only.
 */
public class TestCompoundSettingsAfterReopen extends LuceneTestCase {

  private static IndexWriterConfig config(Codec codec, boolean useCompoundFile) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(codec);
    if (useCompoundFile == false) {
      // MIGRATE.md: compound-file settings for merges are now configured on the codec
      iwc.getCodec().compoundFormat().setShouldUseCompoundFile(false);
      iwc.setUseCompoundFile(false); // flushed segments
    }
    iwc.setMergePolicy(new LogByteSizeMergePolicy());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return iwc;
  }

  private static SegmentCommitInfo onlySegment(Directory dir) throws IOException {
    SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
    assertEquals(1, sis.size());
    return sis.info(0);
  }

  public void testForceMergeOfMergedIndexAfterReopen() throws Exception {
    // a codec instance of its own, e.g. to choose the stored-fields compression mode
    assertNoRewriteAfterReopen(new Lucene104Codec(Lucene104Codec.Mode.BEST_COMPRESSION), false);
  }

  /**
   * Control: a codec instance of its own with the default compound-file settings does not rewrite
   * (the settings differ from the named instance's only in the failing test).
   */
  public void testControlOwnCodecInstanceWithDefaultSettings() throws Exception {
    assertNoRewriteAfterReopen(new Lucene104Codec(Lucene104Codec.Mode.BEST_COMPRESSION), true);
  }

  /**
   * Control: the same scenario with the codec instance that Codec.forName returns (the instance
   * segments read from disk carry) does not rewrite. Note that configuring it changes that shared
   * instance for every writer in the JVM, so the setting is restored afterwards.
   */
  public void testControlWithTheNamedCodecInstance() throws Exception {
    Codec named = Codec.forName("Lucene104");
    boolean saved = named.compoundFormat().getShouldUseCompoundFile();
    try {
      assertNoRewriteAfterReopen(named, false);
    } finally {
      named.compoundFormat().setShouldUseCompoundFile(saved);
    }
  }

  private void assertNoRewriteAfterReopen(Codec codec, boolean useCompoundFile) throws Exception {
    try (Directory dir = newFSDirectory(createTempDir())) {
      try (IndexWriter w = new IndexWriter(dir, config(codec, useCompoundFile))) {
        for (int i = 0; i < 30; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
          w.addDocument(doc);
          if (i % 10 == 9) {
            w.flush();
          }
        }
        w.forceMerge(1);
        w.commit();
        String merged = onlySegment(dir).info.name;
        assertEquals(useCompoundFile, onlySegment(dir).info.getUseCompoundFile());
        // same writer: the merged segment is recognised as merged
        w.forceMerge(1);
        w.commit();
        assertEquals("control: same writer", merged, onlySegment(dir).info.name);
      }

      String before = onlySegment(dir).info.name;
      try (IndexWriter w = new IndexWriter(dir, config(codec, useCompoundFile))) {
        w.forceMerge(1);
        w.commit();
      }
      SegmentCommitInfo after = onlySegment(dir);
      assertEquals(
          "forceMerge(1) after reopening the writer rewrote the already merged segment "
              + before
              + " into "
              + after.info.name
              + "; the segment read from disk carries codec instance "
              + (after.info.getCodec() == codec ? "== writer codec" : "!= writer codec")
              + " whose shouldUseCompoundFile="
              + Codec.forName(codec.getName()).compoundFormat().getShouldUseCompoundFile()
              + " (writer codec: "
              + codec.compoundFormat().getShouldUseCompoundFile()
              + ")",
          before,
          after.info.name);
    }
  }
}
