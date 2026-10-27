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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * ParallelLeafReader: when several readers have the same field, the first reader having the field
 * "wins" (class javadoc and constructor). getFieldInfos() and terms() follow that rule; these tests
 * check that termVectors() follows it too. Public API only.
 */
public class TestParallelLeafReaderTermVectorsOwner extends LuceneTestCase {

  private static final FieldType WITH_TV = new FieldType(TextField.TYPE_NOT_STORED);

  static {
    WITH_TV.setStoreTermVectors(true);
    WITH_TV.freeze();
  }

  private static Field field(String name, String value, boolean termVectors) {
    return new Field(name, value, termVectors ? WITH_TV : TextField.TYPE_NOT_STORED);
  }

  /** An index with one segment holding one document with the given fields. */
  private static DirectoryReader oneDocIndex(Directory dir, Field... fields) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : fields) {
        doc.add(f);
      }
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  private static String firstTerm(Terms terms) throws IOException {
    if (terms == null) {
      return null;
    }
    BytesRef term = terms.iterator().next();
    return term == null ? null : term.utf8ToString();
  }

  /** The (single) term in the term vector of {@code field} for document 0, or null if none. */
  private static String termVectorTerm(LeafReader reader, String field) throws IOException {
    Fields fields = reader.termVectors().get(0);
    return fields == null ? null : firstTerm(fields.terms(field));
  }

  /**
   * The first reader owns "body" and indexes it without term vectors; the second reader has term
   * vectors for "body". The parallel reader says "body" has no term vectors, so it must not return
   * the second reader's.
   */
  public void testNoTermVectorsFromReaderThatDoesNotOwnTheField() throws Exception {
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        DirectoryReader first = oneDocIndex(d1, field("body", "first", false));
        DirectoryReader second = oneDocIndex(d2, field("body", "second", true));
        ParallelLeafReader plr =
            new ParallelLeafReader(false, getOnlyLeafReader(first), getOnlyLeafReader(second))) {
      assertFalse(plr.getFieldInfos().fieldInfo("body").hasTermVectors());
      assertEquals("first", firstTerm(plr.terms("body")));
      assertNull(
          "term vector of 'body' must not come from the second reader",
          termVectorTerm(plr, "body"));
    }
  }

  /**
   * Both readers have term vectors for "body". The first reader must win in both orders. A few
   * independent pairs are checked, because the bug makes the winner depend on object identity.
   */
  public void testFirstReaderWinsWhenBothHaveTermVectors() throws Exception {
    for (int i = 0; i < 4; i++) {
      try (Directory da = new ByteBuffersDirectory();
          Directory db = new ByteBuffersDirectory();
          DirectoryReader a = oneDocIndex(da, field("body", "alpha", true));
          DirectoryReader b = oneDocIndex(db, field("body", "beta", true));
          ParallelLeafReader ab =
              new ParallelLeafReader(false, getOnlyLeafReader(a), getOnlyLeafReader(b));
          ParallelLeafReader ba =
              new ParallelLeafReader(false, getOnlyLeafReader(b), getOnlyLeafReader(a))) {
        assertEquals("alpha", firstTerm(ab.terms("body")));
        assertEquals("beta", firstTerm(ba.terms("body")));
        assertEquals("pair " + i + ", reader order (a, b)", "alpha", termVectorTerm(ab, "body"));
        assertEquals("pair " + i + ", reader order (b, a)", "beta", termVectorTerm(ba, "body"));
      }
    }
  }

  /**
   * Same setup as the first test, plus a vectored "title" in the first reader so the new segment
   * has term vectors. Copying the parallel reader into a new index with addIndexes must give an
   * index without term vectors for "body" that CheckIndex accepts. Level 2 is the lowest CheckIndex
   * level that inspects term vectors.
   */
  public void testAddIndexesOfParallelLeafReaderPassesCheckIndex() throws Exception {
    try (Directory d1 = new ByteBuffersDirectory();
        Directory d2 = new ByteBuffersDirectory();
        Directory dest = new ByteBuffersDirectory();
        DirectoryReader first =
            oneDocIndex(d1, field("body", "first", false), field("title", "heading", true));
        DirectoryReader second = oneDocIndex(d2, field("body", "second", true));
        ParallelLeafReader plr =
            new ParallelLeafReader(false, getOnlyLeafReader(first), getOnlyLeafReader(second))) {
      try (IndexWriter w = new IndexWriter(dest, new IndexWriterConfig())) {
        w.addIndexes(SlowCodecReaderWrapper.wrap(plr));
      }
      String copiedBodyVector;
      try (DirectoryReader copied = DirectoryReader.open(dest)) {
        copiedBodyVector = termVectorTerm(getOnlyLeafReader(copied), "body");
      }
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      CheckIndex.Status status;
      try (CheckIndex checker = new CheckIndex(dest)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        checker.setInfoStream(new PrintStream(log, true, StandardCharsets.UTF_8));
        status = checker.checkIndex();
      }
      StringBuilder errors = new StringBuilder();
      for (String line : log.toString(StandardCharsets.UTF_8).split("\n")) {
        if (line.contains("ERROR")) {
          errors.append(line.trim()).append('\n');
        }
      }
      assertTrue(
          "CheckIndex rejected the new index (its term vector of 'body': "
              + copiedBodyVector
              + "):\n"
              + errors,
          status.clean);
      assertNull("the new index has a term vector for 'body'", copiedBodyVector);
    }
  }
}
