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

import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.Monster;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.tests.util.TimeUnits;

/**
 * NumericDocValues#longValues must behave like the advanceExact/longValue loop in its javadoc.
 *
 * <p>The dense byte-aligned bulk path of the default doc-values format checks {@code size *
 * bytesPerValue <= Integer.MAX_VALUE} and only then adds the padding bytes needed by the 8-byte
 * reads (1 byte for 24-bit values, 3 for 40-bit, 2 for 48-bit, 1 for 56-bit). For exactly one
 * request size per width the padded byte count no longer fits: it wraps to Integer.MIN_VALUE (40
 * and 48 bits) or asks for a byte[] longer than the JVM allows (16, 24 and 56 bits). Each test
 * indexes one segment with exactly that many documents and asks for all of its values in one call.
 *
 * <p>Needs about 8-10 GB of heap for the 40/48/56-bit tests and about 16 GB for the 16-bit test
 * (the docs and values arrays alone are 12 bytes per document).
 * Set {@code -Dtests.monster=true}. {@code -Dbulkdecode.dir=heap} keeps the index on the heap instead
 * of a temporary FSDirectory.
 */
@TimeoutSuite(millis = 4 * TimeUnits.HOUR)
@Monster("indexes 300-430 million documents and needs ~10 GB of heap")
@SuppressSysoutChecks(bugUrl = "prints progress")
public class TestNumericBulkDecodePaddedSize extends LuceneTestCase {

  /** 429,496,729 * 5 = 2,147,483,645 bytes; + 3 padding bytes = 2^31, which wraps. */
  public void test40BitsWholeSegment() throws Exception {
    check(429_496_729, 40);
  }

  /** 357,913,941 * 6 = 2,147,483,646 bytes; + 2 padding bytes = 2^31, which wraps. */
  public void test48BitsWholeSegment() throws Exception {
    check(357_913_941, 48);
  }

  /** 306,783,378 * 7 = 2,147,483,646 bytes; + 1 padding byte = Integer.MAX_VALUE: too long. */
  public void test56BitsWholeSegment() throws Exception {
    check(306_783_378, 56);
  }

  /** 715,827,882 * 3 = 2,147,483,646 bytes; + 1 padding byte = Integer.MAX_VALUE: too long. */
  public void test24BitsWholeSegment() throws Exception {
    check(715_827_882, 24);
  }

  /** 1,073,741,823 * 2 = 2,147,483,646 bytes (no padding), above the JVM's byte[] limit. */
  public void test16BitsWholeSegment() throws Exception {
    check(1_073_741_823, 16);
  }

  private static boolean heapDirectory() {
    return "heap".equals(System.getProperty("bulkdecode.dir"));
  }

  private Directory openDirectory() throws IOException {
    if (heapDirectory()) {
      return new ByteBuffersDirectory();
    }
    return FSDirectory.open(createTempDir("bulkDecodePaddedSize"));
  }

  private void check(int exactSize, int bitsPerValue) throws Exception {
    // controls only: -Dbulkdecode.sizeDelta=-1 or 1 moves off the single failing size (both pass)
    final int numDocs = exactSize + Integer.getInteger("bulkdecode.sizeDelta", 0);
    try (Directory dir = openDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(null)
              .setCodec(TestUtil.getDefaultCodec())
              .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
              .setRAMBufferSizeMB(1900)
              .setRAMPerThreadHardLimitMB(2047)
              .setMergePolicy(new LogDocMergePolicy())
              // the in-heap directory cannot build a compound file from a >= 2 GiB file (a separate
              // issue in ByteBuffersDataOutput#copyBytes), so skip compound files there; it does not
              // change the doc-values file that this test reads
              .setUseCompoundFile(heapDirectory() == false)
              .setOpenMode(IndexWriterConfig.OpenMode.CREATE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        NumericDocValuesField field = new NumericDocValuesField("f", 0);
        doc.add(field);
        for (int i = 0; i < numDocs; i++) {
          // small values 0, 1, 2, ... plus one large value at the start of every 16384-doc block:
          // every block needs bitsPerValue bits, so the writer picks one bitsPerValue-wide block
          // of packed values (no table, gcd 1, no per-block widths)
          long low = i & ((1L << Math.min(bitsPerValue - 2, 30)) - 1);
          field.setLongValue(i % 16384 == 0 ? (1L << (bitsPerValue - 1)) | low : low);
          w.addDocument(doc);
          if (i % 50_000_000 == 0) {
            System.out.println("indexed " + i + " of " + numDocs);
          }
        }
        w.forceMerge(1);
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        assertEquals(numDocs, leaf.maxDoc());

        int[] docs = new int[numDocs];
        for (int i = 0; i < numDocs; i++) {
          docs[i] = i;
        }
        long[] values = new long[numDocs];

        NumericDocValues bulk = leaf.getNumericDocValues("f");
        try {
          bulk.longValues(numDocs, docs, values, -1L);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
          throw new AssertionError(
              "longValues("
                  + numDocs
                  + " contiguous docs) threw on a dense "
                  + bitsPerValue
                  + "-bit field, while the per-document loop of its javadoc returns every value",
              e);
        }

        NumericDocValues perDoc = leaf.getNumericDocValues("f");
        for (int i = 0; i < numDocs; i++) {
          assertTrue(perDoc.advanceExact(i));
          long expected = perDoc.longValue();
          if (values[i] != expected) {
            fail("doc " + i + ": bulk value " + values[i] + " != per-document value " + expected);
          }
        }
        assertEquals(numDocs - 1, bulk.docID());
      }
    }
  }
}
