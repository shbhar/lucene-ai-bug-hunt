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

import java.util.List;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCodecHeaderFooterMagicAsDocumented extends LuceneTestCase {

  public void testMagicReadAsDocumented() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        CodecUtil.writeHeader(out, "Foo", 1);
        CodecUtil.writeFooter(out);
      }
      try (IndexInput in = dir.openInput("f", IOContext.READONCE)) {
        int headerMagic = in.readInt();
        in.seek(in.length() - CodecUtil.footerLength());
        int footerMagic = in.readInt();
        // Expected: Magic --> DataOutput#writeInt reads back as CODEC_MAGIC / FOOTER_MAGIC; actual: byte-swapped.
        assertEquals(
            List.of(CodecUtil.CODEC_MAGIC, CodecUtil.FOOTER_MAGIC), List.of(headerMagic, footerMagic));
      }
    }
  }
}
