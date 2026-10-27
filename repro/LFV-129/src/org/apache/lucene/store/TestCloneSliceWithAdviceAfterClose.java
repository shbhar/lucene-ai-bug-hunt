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
package org.apache.lucene.store;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Using a clone after the original input was closed throws AlreadyClosedException (IndexInput
 * javadoc, TestMultiMMap#testCloneSafety). Here the clone is sliced with an IOContext whose read
 * advice is not NORMAL, so the slice is madvised.
 */
public class TestCloneSliceWithAdviceAfterClose extends LuceneTestCase {

  public void testSliceWithAdviceOnCloneAfterClose() throws Exception {
    assumeTrue("needs madvise", MMapDirectory.supportsMadvise());
    try (MMapDirectory dir = new MMapDirectory(createTempDir())) {
      dir.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
      final int length = 1 << 20; // larger than a page, so the slice is advised
      try (IndexOutput out = dir.createOutput("data", IOContext.DEFAULT)) {
        out.writeBytes(new byte[length], length);
      }
      IndexInput in = dir.openInput("data", IOContext.DEFAULT);
      IndexInput clone = in.clone();
      in.close();
      expectThrows(AlreadyClosedException.class, clone::readByte);
      try {
        clone.slice("s", 0, length, IOContext.DEFAULT.withHints(DataAccessHint.RANDOM));
        fail("expected AlreadyClosedException");
      } catch (AlreadyClosedException _) {
        // correct
      } catch (Throwable t) {
        throw new AssertionError("expected AlreadyClosedException, got " + t, t);
      }
    }
  }
}
