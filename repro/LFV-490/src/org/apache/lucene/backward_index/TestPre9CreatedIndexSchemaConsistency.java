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
package org.apache.lucene.backward_index;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Indexes created by Lucene 8 and then fully re-indexed by Lucene 9, so that every segment has
 * version = minVersion = 9.12.3, open on Lucene 10.4+ since GITHUB#14607 ("Index open performs
 * version check on each segment, ignores indexCreatedVersionMajor").
 *
 * <p>Lucene 9 never enforced LUCENE-9334 field consistency on such indexes (LUCENE-10518,
 * GITHUB#11554: FieldNumbers#strictlyConsistent = indexCreatedVersionMajor >= 9), neither for the
 * old segments nor for the segments Lucene 9 itself wrote, and it disabled the FieldExistsQuery
 * optimizations that depend on consistency for them (FieldExistsQuery#hasStrictlyConsistentFieldInfos).
 * The fixtures (fixture-src/) were written by the 8.11.4 and 9.12.3 release jars:
 *
 * <ul>
 *   <li>legacy8-exists.zip: Lucene 9's own TestFieldExistsQuery#testOldIndices documents in two
 *       segments: d1 has my_field as StringField + BinaryDocValuesField, d2 only the StringField.
 *   <li>legacy8-exists-merged.zip: the same documents after a forceMerge(1) by 9.12.3.
 * </ul>
 */
public class TestPre9CreatedIndexSchemaConsistency extends LuceneTestCase {

  private Path unzip(String name) throws Exception {
    Path dir = createTempDir(name);
    try (InputStream in = getClass().getResourceAsStream(name + ".zip");
        ZipInputStream zis = new ZipInputStream(in)) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        Files.copy(zis, dir.resolve(e.getName()));
      }
    }
    return dir;
  }

  /** The index is one main accepts: created by 8.x, every segment written by 9.12.3. */
  private static void assertReindexedLegacyIndex(Directory dir) throws Exception {
    SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
    assertEquals(8, sis.getIndexCreatedVersionMajor());
    for (SegmentCommitInfo si : sis) {
      assertEquals("9.12.3", si.info.getMinVersion().toString());
    }
  }

  private static List<String> ids(IndexSearcher searcher, TopDocs td) throws Exception {
    List<String> ids = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      ids.add(searcher.getIndexReader().storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }

  /** Lucene 9's testOldIndices: two segments, only d1 has a doc value for my_field. */
  public void testFieldExistsSearchUnmerged() throws Exception {
    try (Directory dir = newFSDirectory(unzip("legacy8-exists"))) {
      assertReindexedLegacyIndex(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs td = searcher.search(new FieldExistsQuery("my_field"), 10);
        assertEquals(List.of("d1"), ids(searcher, td));
      }
    }
  }

  public void testFieldExistsCountUnmerged() throws Exception {
    try (Directory dir = newFSDirectory(unzip("legacy8-exists"))) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, new IndexSearcher(reader).count(new FieldExistsQuery("my_field")));
      }
    }
  }

  /** The same documents after Lucene 9 merged them into one segment. */
  public void testFieldExistsSearchMerged() throws Exception {
    try (Directory dir = newFSDirectory(unzip("legacy8-exists-merged"))) {
      assertReindexedLegacyIndex(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs td = searcher.search(new FieldExistsQuery("my_field"), 10);
        assertEquals("only d1 has a doc value for my_field", List.of("d1"), ids(searcher, td));
      }
    }
  }

  public void testFieldExistsCountMerged() throws Exception {
    try (Directory dir = newFSDirectory(unzip("legacy8-exists-merged"))) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, new IndexSearcher(reader).count(new FieldExistsQuery("my_field")));
      }
    }
  }

  /** DirectoryReader.open accepts the index, so IndexWriter (APPEND) should too, as 9.x does. */
  public void testIndexWriterOpensUnmerged() throws Exception {
    try (Directory dir = newFSDirectory(unzip("legacy8-exists"))) {
      assertReindexedLegacyIndex(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND);
      IndexWriter w;
      try {
        w = new IndexWriter(dir, iwc);
      } catch (IllegalArgumentException e) {
        throw new AssertionError(
            "IndexWriter refuses an index that DirectoryReader.open accepts: " + e.getMessage(), e);
      }
      try (w) {
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.numDocs());
      }
      TestUtil.checkIndex(dir);
    }
  }

  /** FieldInfos.getMergedFieldInfos (used by e.g. UnifiedHighlighter) over the same reader. */
  public void testMergedFieldInfosUnmerged() throws Exception {
    try (Directory dir = newFSDirectory(unzip("legacy8-exists"))) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        FieldInfos merged;
        try {
          merged = FieldInfos.getMergedFieldInfos(reader);
        } catch (IllegalArgumentException e) {
          throw new AssertionError("getMergedFieldInfos fails: " + e.getMessage(), e);
        }
        assertNotNull(merged.fieldInfo("my_field"));
      }
    }
  }
}
