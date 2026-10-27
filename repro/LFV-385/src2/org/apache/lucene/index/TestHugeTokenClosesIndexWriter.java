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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * IndexWriter#addDocument documents: "each term in the document can be no longer than
 * MAX_TERM_LENGTH in bytes, otherwise an IllegalArgumentException will be thrown", and an immense
 * term is a non-aborting error (the document is skipped, the writer stays usable).
 *
 * <p>A single token of 715,827,878 or more UTF-16 units is sized for UTF-8 with 3 bytes per unit
 * (BytesRefBuilder.copyChars -> UnicodeUtil.maxUTF8Length) inside the aborting step of the indexing
 * chain (IndexingChain: termsHashPerField.add(termAttribute.getBytesRef(), ...) is wrapped in catch
 * (Throwable) -> onAbortingException). The sizing fails (ArithmeticException from multiplyExact, or
 * IllegalArgumentException from ArrayUtil.oversize) before the immense-term check runs, so the
 * IndexWriter closes and all buffered documents are lost.
 *
 * <p>Needs about 3 GB of heap (a 716 MB Latin-1 String plus the 1.6 GB term buffer).
 */
public class TestHugeTokenClosesIndexWriter extends LuceneTestCase {

  /** 3 * n overflows int: maxUTF8Length throws ArithmeticException. */
  static final int OVERFLOWING_LENGTH = Integer.MAX_VALUE / 3 + 1; // 715,827,883

  /** 3 * n fits in int but exceeds ArrayUtil.MAX_ARRAY_LENGTH: oversize throws IAE. */
  static final int OVERSIZE_LENGTH = 715_827_878;

  private static FieldType untokenizedIndexed() {
    FieldType ft = new FieldType();
    ft.setIndexOptions(IndexOptions.DOCS);
    ft.setTokenized(false);
    ft.setOmitNorms(true);
    ft.freeze();
    return ft;
  }

  private void assertHugeTokenIsRejectedWithoutClosingWriter(int tokenLength) throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document ok = new Document();
      ok.add(new StringField("id", "buffered-before", Field.Store.YES));
      w.addDocument(ok);

      Document huge = new Document();
      huge.add(new Field("body", "a".repeat(tokenLength), untokenizedIndexed()));
      Throwable thrown = null;
      try {
        w.addDocument(huge);
      } catch (Throwable t) {
        thrown = t;
      }
      huge = null;
      assertNotNull("the immense term must be rejected", thrown);
      assertTrue(
          "IndexWriter must stay open after an immense term; it closed with tragic exception "
              + w.getTragicException()
              + " (addDocument threw "
              + thrown
              + ")"
              + topFrames(thrown),
          w.isOpen());
      assertTrue(
          "documented exception is IllegalArgumentException, got " + thrown,
          thrown instanceof IllegalArgumentException);

      // the writer is still usable and the document buffered before the failure survives
      Document after = new Document();
      after.add(new StringField("id", "added-after", Field.Store.YES));
      w.addDocument(after);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }

  private static String topFrames(Throwable t) {
    StringBuilder sb = new StringBuilder();
    StackTraceElement[] st = t.getStackTrace();
    for (int i = 0; i < Math.min(8, st.length); i++) {
      sb.append("\n      at ").append(st[i]);
    }
    return sb.toString();
  }

  /** Control: an immense term of 40,000 units takes the documented non-aborting path. */
  public void testImmenseTermIsNonAborting() throws Exception {
    assertHugeTokenIsRejectedWithoutClosingWriter(40_000);
  }

  public void testTokenOfMaxIntDiv3PlusOneUnitsClosesWriter() throws Exception {
    assertHugeTokenIsRejectedWithoutClosingWriter(OVERFLOWING_LENGTH);
  }

  public void testTokenJustAboveMaxArrayLengthDiv3ClosesWriter() throws Exception {
    assertHugeTokenIsRejectedWithoutClosingWriter(OVERSIZE_LENGTH);
  }
}
