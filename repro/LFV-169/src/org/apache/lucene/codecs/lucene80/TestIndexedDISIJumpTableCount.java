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
package org.apache.lucene.codecs.lucene80;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.LuceneTestCase;

/**
 * IndexedDISI.writeBitSet returns the number of jump-table entries as a {@code short}. A doc set
 * whose last doc is in block 32766 or 32767 (doc id >= 32766 * 65536 = 2,147,352,576, which is
 * below IndexWriter.MAX_DOCS) makes the writer emit 32768 or 32769 entries; the returned value
 * wraps to a negative short, so every reader (createJumpTable returns null for a count <= 0)
 * silently ignores the jump table that was written, and advance() falls back to a block-by-block
 * scan.
 */
public class TestIndexedDISIJumpTableCount extends LuceneTestCase {

  /** Last block holding a doc. 32766 is the smallest block id that triggers the wrap. */
  private static final int LAST_BLOCK = 32766;

  /** One doc in every block 0..LAST_BLOCK, so that every block is non-empty. */
  private static final class OneDocPerBlock extends DocIdSetIterator {
    private int doc = -1;

    @Override
    public int docID() {
      return doc;
    }

    @Override
    public int nextDoc() {
      return advance(doc + 1);
    }

    @Override
    public int advance(int target) {
      int block = (target + 0xFFFF) >>> 16; // first block start >= target
      return doc = block <= LAST_BLOCK ? block << 16 : NO_MORE_DOCS;
    }

    @Override
    public long cost() {
      return LAST_BLOCK + 1;
    }
  }

  public void testReturnedJumpTableEntryCount() throws IOException {
    assertTrue((long) LAST_BLOCK << 16 < IndexWriter.MAX_DOCS);
    try (Directory dir = new ByteBuffersDirectory()) {
      short count;
      long length;
      try (IndexOutput out = dir.createOutput("disi", IOContext.DEFAULT)) {
        count =
            IndexedDISI.writeBitSet(
                new OneDocPerBlock(), out, IndexedDISI.DEFAULT_DENSE_RANK_POWER);
        length = out.getFilePointer();
      }
      // Layout: LAST_BLOCK + 1 SPARSE blocks with one doc (2 + 2 + 2 bytes each), the
      // NO_MORE_DOCS block (6 bytes), then the jump table (8 bytes per entry).
      long blockBytes = (LAST_BLOCK + 2) * 6L;
      long entriesWritten = (length - blockBytes) / Long.BYTES;
      assertEquals(0, (length - blockBytes) % Long.BYTES);
      assertEquals(LAST_BLOCK + 2, entriesWritten);
      // writeBitSet javadoc: "@return the number of jump-table entries following the blocks, -1
      // for no entries."
      assertEquals(
          "writeBitSet must return the number of jump-table entries it wrote",
          entriesWritten,
          (long) count);
    }
  }

  public void testAdvanceUsesTheJumpTable() throws IOException {
    AtomicLong blockSeeks = new AtomicLong();
    try (Directory dir = new CountingDirectory(new ByteBuffersDirectory(), blockSeeks)) {
      short count;
      long length;
      try (IndexOutput out = dir.createOutput("disi", IOContext.DEFAULT)) {
        count =
            IndexedDISI.writeBitSet(
                new OneDocPerBlock(), out, IndexedDISI.DEFAULT_DENSE_RANK_POWER);
        length = out.getFilePointer();
      }
      int target = LAST_BLOCK << 16;
      try (IndexInput in = dir.openInput("disi", IOContext.DEFAULT)) {
        IndexedDISI disi =
            new IndexedDISI(
                in, 0L, length, count, IndexedDISI.DEFAULT_DENSE_RANK_POWER, LAST_BLOCK + 1);
        blockSeeks.set(0);
        assertEquals(target, disi.advance(target)); // results stay correct ...
        assertEquals(LAST_BLOCK, disi.index());
        // ... but the class javadoc promises "direct jumping to the block" via the lookup table:
        // one seek. With the wrapped count the jump table is ignored and every block is visited.
        assertTrue(
            "advance to the last block took " + blockSeeks.get() + " block seeks (count=" + count
                + ")",
            blockSeeks.get() <= 2);
      }
    }
  }

  /** Counts seek() calls on the "docs" slices that IndexedDISI creates (createBlockSlice). */
  private static final class CountingDirectory extends FilterDirectory {
    private final AtomicLong blockSeeks;

    CountingDirectory(Directory in, AtomicLong blockSeeks) {
      super(in);
      this.blockSeeks = blockSeeks;
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      return new CountingInput(in.openInput(name, context), blockSeeks, false);
    }
  }

  private static final class CountingInput extends IndexInput {
    private final IndexInput in;
    private final AtomicLong blockSeeks;
    private final boolean count;

    CountingInput(IndexInput in, AtomicLong blockSeeks, boolean count) {
      super("counting(" + in + ")");
      this.in = in;
      this.blockSeeks = blockSeeks;
      this.count = count;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    @Override
    public long getFilePointer() {
      return in.getFilePointer();
    }

    @Override
    public void seek(long pos) throws IOException {
      if (count) {
        blockSeeks.incrementAndGet();
      }
      in.seek(pos);
    }

    @Override
    public long length() {
      return in.length();
    }

    @Override
    public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
      return new CountingInput(
          in.slice(sliceDescription, offset, length), blockSeeks, "docs".equals(sliceDescription));
    }

    @Override
    public IndexInput clone() {
      return new CountingInput(in.clone(), blockSeeks, count);
    }

    @Override
    public byte readByte() throws IOException {
      return in.readByte();
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
      in.readBytes(b, offset, len);
    }

    @Override
    public short readShort() throws IOException {
      return in.readShort();
    }

    @Override
    public int readInt() throws IOException {
      return in.readInt();
    }

    @Override
    public long readLong() throws IOException {
      return in.readLong();
    }
  }
}
