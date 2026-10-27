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
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Constants;

/** LUCENE-5574 */
@SuppressFileSystems("WindowsFS") // the bug doesn't happen on windows.
public class TestNRTReaderCleanup extends LuceneTestCase {

  public void testClosingNRTReaderDoesNotCorruptYourIndex() throws IOException {

    // Windows disallows deleting & overwriting files still
    // open for reading:
    assumeFalse("this test can't run on Windows", Constants.WINDOWS);

    MockDirectoryWrapper dir = newMockDirectory();

    IndexWriterConfig iwc = newIndexWriterConfig(new MockAnalyzer(random()));
    LogMergePolicy lmp = new LogDocMergePolicy();
    lmp.setMergeFactor(2);
    iwc.setMergePolicy(lmp);

    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new TextField("a", "foo", Field.Store.NO));
    w.addDocument(doc);
    w.commit();
    w.addDocument(doc);

    // Get a new reader, but this also sets off a merge:
    IndexReader r = w.getReader();
    w.close();

    // Blow away index and make a new writer:
    for (String name : dir.listAll()) {
      dir.deleteFile(name);
    }

    w = new RandomIndexWriter(random(), dir);
    w.addDocument(doc);
    w.close();
    r.close();
    dir.close();
  }

  public void testMinimalDefaultConfig() throws IOException {
    assumeFalse("Windows refuses to delete open files", Constants.WINDOWS);
    try (Directory dir = FSDirectory.open(createTempDir());
        Directory target = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, defaultConfig());
      w1.addDocument(storedId("old"));
      DirectoryReader reader = DirectoryReader.open(w1); // flushes segment _0, never committed
      w1.rollback(); // the reader stays open
      new IndexWriter(dir, defaultConfig()).rollback(); // deletes _0's files, commits nothing
      try (IndexWriter w3 = new IndexWriter(dir, defaultConfig())) {
        w3.addDocument(storedId("new")); // close() commits a different segment, also named _0
      }
      CodecReader leaf = (CodecReader) getOnlyLeafReader(reader);
      System.out.println(
          "stale reader: segment=" + ((SegmentReader) leaf).getSegmentName()
              + " id=" + leaf.storedFields().document(0).get("id")
              + " | latest commit: segment=" + SegmentInfos.readLatestCommit(dir).info(0).info.name);
      try (IndexWriter t = new IndexWriter(target, defaultConfig())) {
        t.addIndexes(leaf); // copy the stale reader's segment into another index
      }
      reader.close();
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        System.out.println("addIndexes copy: id=" + copy.storedFields().document(0).get("id"));
        assertEquals("old", copy.storedFields().document(0).get("id"));
      }
    }
  }

  private static IndexWriterConfig defaultConfig() {
    return new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
  }

  private static Document storedId(String id) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    return doc;
  }

  public void testMinimalAdviseByContext() throws IOException {
    // 4096 letters: the stored-fields data inside the compound file is at least a page
    assertMinimalAdviseByContextCopiesTheReadersValue(
        value(0).substring(0, 4096), value(1).substring(0, 4096));
  }

  public void testMinimalAdviseByContextUnderAPage() throws IOException {
    // control: the 3-letter values of testMinimalDefaultConfig are under a page
    assertMinimalAdviseByContextCopiesTheReadersValue("old", "new");
  }

  private void assertMinimalAdviseByContextCopiesTheReadersValue(String old, String other)
      throws IOException {
    assumeFalse("Windows refuses to delete open files", Constants.WINDOWS);
    try (MMapDirectory dir = new MMapDirectory(createTempDir());
        Directory target = new ByteBuffersDirectory()) {
      dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT); // the one non-default setting
      IndexWriter w1 = new IndexWriter(dir, defaultConfig());
      w1.addDocument(storedId(old));
      DirectoryReader reader = DirectoryReader.open(w1); // flushes segment _0, never committed
      w1.rollback(); // the reader stays open
      new IndexWriter(dir, defaultConfig()).rollback(); // deletes _0's files, commits nothing
      try (IndexWriter w3 = new IndexWriter(dir, defaultConfig())) {
        w3.addDocument(storedId(other)); // close() commits a different segment, also named _0
      }
      SegmentReader leaf = (SegmentReader) getOnlyLeafReader(reader);
      System.out.println(
          "v3 stale reader: segment="
              + leaf.getSegmentName()
              + " compound="
              + leaf.getSegmentInfo().info.getUseCompoundFile()
              + " inner .fdt="
              + leaf.core.cfsReader.fileLength(leaf.getSegmentName() + ".fdt")
              + " B id="
              + head(leaf.storedFields().document(0).get("id"))
              + " | latest commit: segment="
              + SegmentInfos.readLatestCommit(dir).info(0).info.name);
      try (IndexWriter t = new IndexWriter(target, defaultConfig())) {
        t.addIndexes(leaf); // copy the stale reader's segment into another index
      }
      reader.close();
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        String copied = copy.storedFields().document(0).get("id");
        System.out.println(
            "v3 addIndexes copy: id=" + head(copied) + " (" + copied.length() + " letters)");
        assertEquals(head(old), head(copied)); // a short message
        assertEquals(old, copied);
      }
    }
  }

  private static String head(String value) {
    return value.length() > 12 ? value.substring(0, 12) + "..." : value;
  }

  public void testAddIndexesCopiesTheStoredFieldsOfAReaderWhoseSegmentNameIsReused()
      throws IOException {
    assertAddIndexesCopiesWhatTheReaderReads(false, false, 1, 1);
  }

  public void testAddIndexesCopiesTheVectorsOfAReaderWhoseSegmentNameIsReused() throws IOException {
    assertAddIndexesCopiesWhatTheReaderReads(false, true, 20, 20);
  }

  public void testAddIndexesCopiesTheCompoundFileOfAReaderWhoseSegmentNameIsReused()
      throws IOException {
    // more docs than the segment that reuses the name, so its compound file is the larger one
    assertAddIndexesCopiesWhatTheReaderReads(true, false, 20, 1);
  }

  /**
   * An NRT reader outlives the writer that rolled back its segment. The next writer deletes the
   * segment's files and commits nothing, so the one after it names its segment the same. Merging
   * the reader must still read the files the reader has open, not the new files with that name.
   */
  private void assertAddIndexesCopiesWhatTheReaderReads(
      boolean useCompoundFile, boolean vectors, int numDocs, int numNewDocs) throws IOException {
    // Windows disallows deleting files still open for reading:
    assumeFalse("this test can't run on Windows", Constants.WINDOWS);

    try (MMapDirectory dir = new MMapDirectory(createTempDir());
        Directory target = new ByteBuffersDirectory()) {
      // searches and merges advise different access, so merges read through mappings of their own
      dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
      IndexWriter w = new IndexWriter(dir, config(useCompoundFile));
      for (int i = 0; i < numDocs; i++) {
        w.addDocument(doc(vectors, i));
      }
      DirectoryReader reader = DirectoryReader.open(w);
      w.rollback();
      new IndexWriter(dir, config(useCompoundFile)).rollback();
      try (IndexWriter w3 = new IndexWriter(dir, config(useCompoundFile))) {
        for (int i = 0; i < numNewDocs; i++) {
          w3.addDocument(doc(vectors, numDocs + i));
        }
      }

      try (reader) {
        SegmentReader leaf = (SegmentReader) getOnlyLeafReader(reader);
        assertEquals(
            "the next writer reuses the segment name",
            leaf.getSegmentName(),
            SegmentInfos.readLatestCommit(dir).info(0).info.name);
        try (IndexWriter t = new IndexWriter(target, config(false))) {
          t.addIndexes(leaf);
        }
      }

      try (DirectoryReader copy = DirectoryReader.open(target)) {
        LeafReader leaf = getOnlyLeafReader(copy);
        assertEquals(numDocs, leaf.maxDoc());
        StoredFields storedFields = leaf.storedFields();
        FloatVectorValues vectorValues = leaf.getFloatVectorValues("vector");
        for (int i = 0; i < numDocs; i++) {
          if (vectors) {
            assertArrayEquals(vector(i), vectorValues.vectorValue(i), 0f);
          } else {
            String value = storedFields.document(i).get("id");
            assertEquals(value(i).substring(0, 20), value.substring(0, 20)); // a short message
            assertEquals(value(i), value);
          }
        }
      }
    }
  }

  private static IndexWriterConfig config(boolean useCompoundFile) {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec()); // the merge mappings are the default formats'
    iwc.setUseCompoundFile(useCompoundFile);
    return iwc;
  }

  private static Document doc(boolean vectors, int n) {
    Document doc = new Document();
    if (vectors) {
      doc.add(new KnnFloatVectorField("vector", vector(n)));
    } else {
      doc.add(new StoredField("id", value(n)));
    }
    return doc;
  }

  /** Larger than a page, and as long once compressed whatever {@code n}, which shifts letters. */
  private static String value(int n) {
    StringBuilder value = new StringBuilder();
    long x = 42;
    while (value.length() < 80_000) {
      x = x * 6364136223846793005L + 1442695040888963407L;
      value.append((char) ('a' + (int) ((x >>> 33) % 26 + n) % 26));
    }
    return value.toString();
  }

  private static float[] vector(int n) {
    float[] vector = new float[1024];
    Arrays.fill(vector, n + 1);
    return vector;
  }
}
