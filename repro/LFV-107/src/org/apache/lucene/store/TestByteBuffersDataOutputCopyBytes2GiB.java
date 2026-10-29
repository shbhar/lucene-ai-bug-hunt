package org.apache.lucene.store;

import java.io.IOException;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestByteBuffersDataOutputCopyBytes2GiB extends LuceneTestCase {

  public void testCopyBytesCopiesAllBytes() throws IOException {
    DataInput zeros =
        new DataInput() {
          @Override
          public byte readByte() {
            return 0;
          }

          @Override
          public void readBytes(byte[] b, int offset, int len) {}

          @Override
          public void skipBytes(long numBytes) {}
        };
    long numBytes = 1L << 31;
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    out.copyBytes(zeros, numBytes);
    // expected: size() == 2^31 (every byte copied); actual: 0, since (int) numBytes is negative
    assertEquals(numBytes, out.size());
  }
}
