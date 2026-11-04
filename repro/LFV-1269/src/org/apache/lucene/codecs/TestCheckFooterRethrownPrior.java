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
package org.apache.lucene.codecs;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.ChecksumIndexInput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCheckFooterRethrownPrior extends LuceneTestCase {

  public void testFooterReadRethrowsPrior() {
    CorruptIndexException failure = new CorruptIndexException("read failed", "input");
    ChecksumIndexInput in =
        new ChecksumIndexInput("input") {
          @Override
          public long getChecksum() {
            return 0;
          }

          @Override
          public long length() {
            return CodecUtil.footerLength();
          }

          @Override
          public long getFilePointer() {
            return 0;
          }

          @Override
          public byte readByte() throws CorruptIndexException {
            throw failure;
          }

          @Override
          public void readBytes(byte[] b, int offset, int len) throws CorruptIndexException {
            throw failure;
          }

          @Override
          public void close() {}

          @Override
          public IndexInput slice(String desc, long offset, long length) {
            throw new UnsupportedOperationException();
          }
        };
    // expected: CorruptIndexException; actual: IllegalArgumentException "Self-suppression not permitted"
    expectThrows(CorruptIndexException.class, () -> CodecUtil.checkFooter(in, failure));
  }
}
