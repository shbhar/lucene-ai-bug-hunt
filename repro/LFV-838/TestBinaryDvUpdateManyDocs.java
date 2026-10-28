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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * One call to {@link IndexWriter#updateBinaryDocValue(Term, String, BytesRef)} whose term matches
 * many documents of one segment. The javadoc documents no size limit and says the value replaces
 * the value of all affected documents. Expected: the commit succeeds, the writer stays open, the
 * acknowledged document added before the update is committed and every matched document reads
 * back the new value.
 *
 * <p>Heap: the failing run needs about 4 GB (the per-document copies of the value); run with
 * -Xmx8g. The control method needs far less.
 */
public class TestBinaryDvUpdateManyDocs extends LuceneTestCase {

  private static final int VALUE_LEN = 1 << 20; // 1 MiB

  /** 2,100 documents x 1 MiB = 2,100 MiB of updated values in one segment (> 2^31 bytes). */
  public void testUpdateMatchingManyDocs() throws Exception {
    runScenario(Integer.getInteger("v1.numDocs", 2100));
  }

  /** Control: the same scenario with 100 documents (100 MiB) must work on every version. */
  public void testControlFewDocs() throws Exception {
    runScenario(100);
  }

  private static void runScenario(int numDocs) throws Exception {
    byte[] big = new byte[VALUE_LEN];
    for (int i = 0; i < big.length; i++) {
      big[i] = (byte) (i * 31 + 7);
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      for (int i = 0; i < numDocs; i++) {
        Document d = new Document();
        d.add(new StringField("id", "d" + i, Field.Store.NO));
        d.add(new StringField("group", "g", Field.Store.NO));
        d.add(new BinaryDocValuesField("bdv", new BytesRef(new byte[] {1})));
        w.addDocument(d);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("setup: one segment", 1, r.leaves().size());
        assertEquals(numDocs, r.numDocs());
      }

      // An unrelated, acknowledged but not yet committed document.
      Document late = new Document();
      late.add(new StringField("id", "late", Field.Store.NO));
      late.add(new BinaryDocValuesField("bdv", new BytesRef(new byte[] {2})));
      w.addDocument(late);

      // One update whose term matches all numDocs documents of the committed segment.
      w.updateBinaryDocValue(new Term("group", "g"), "bdv", new BytesRef(big));

      Throwable failure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        failure = t;
      }
      boolean open = w.isOpen();
      Throwable tragic = w.getTragicException();
      if (open) {
        w.close();
      }

      int committedDocs;
      int lateHits;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committedDocs = r.numDocs();
        lateHits = new IndexSearcher(r).count(new TermQuery(new Term("id", "late")));
        if (failure == null) {
          // spot-check updated values on a few matched documents
          for (LeafReaderContext ctx : r.leaves()) {
            BinaryDocValues bdv = ctx.reader().getBinaryDocValues("bdv");
            assertNotNull(bdv);
            int checked = 0;
            for (int doc = 0; doc < ctx.reader().maxDoc() && checked < 3; doc += 997) {
              assertTrue(bdv.advanceExact(doc));
              BytesRef v = bdv.binaryValue();
              if (v.length == VALUE_LEN) {
                assertTrue(
                    "value mismatch at doc " + doc,
                    Arrays.equals(big, 0, VALUE_LEN, v.bytes, v.offset, v.offset + v.length));
                checked++;
              }
            }
          }
        }
      }
      // A new writer on the same directory opens fine (the failure does not persist).
      boolean reopenedWriterOk;
      try (IndexWriter w2 = new IndexWriter(dir, new IndexWriterConfig())) {
        reopenedWriterOk = w2.isOpen();
      }

      String report =
          "numDocs="
              + numDocs
              + " valueLen="
              + VALUE_LEN
              + " product="
              + ((long) numDocs * VALUE_LEN)
              + " (2^31="
              + (1L << 31)
              + ")\n  commit failure: "
              + describe(failure)
              + "\n  writer open after commit: "
              + open
              + "\n  tragic exception: "
              + (tragic == null ? "null" : tragic.getClass().getName() + ": " + tragic.getMessage())
              + "\n  after reopen: committed docs="
              + committedDocs
              + ", late doc present="
              + (lateHits == 1)
              + ", new writer opens="
              + reopenedWriterOk;
      System.out.println("V1-REPORT " + report);

      assertNull("commit must not fail\n" + report, failure);
      assertTrue("writer must stay open\n" + report, open);
      assertNull("no tragic exception\n" + report, tragic);
      assertEquals("all docs incl. the late one committed\n" + report, numDocs + 1, committedDocs);
      assertEquals("late doc committed\n" + report, 1, lateHits);
    }
  }

  private static String describe(Throwable t) {
    if (t == null) {
      return "none";
    }
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    String[] lines = sw.toString().split("\n");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(14, lines.length); i++) {
      sb.append("\n      ").append(lines[i].trim());
    }
    return sb.toString();
  }
}
