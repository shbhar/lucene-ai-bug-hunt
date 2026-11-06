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
package org.apache.lucene.codecs.lucene103.blocktree;

import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestStatsSuffixBytesPerBlock extends LuceneTestCase {

  public void testBeforeCompressionAverage() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc = new Document();
        for (int i = 0; i < 10; i++) {
          doc.add(new StringField("f", "t" + i, Field.Store.NO));
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Stats s = (Stats) r.leaves().get(0).reader().terms("f").getStats();
        // expected: the line's average is its own total / blocks; actual: compressed bytes / blocks
        String expected =
            String.format(
                Locale.ROOT,
                "%d term suffix bytes before compression (%.1f suffix-bytes/block)",
                s.totalUncompressedBlockSuffixBytes,
                (double) s.totalUncompressedBlockSuffixBytes / s.totalBlockCount);
        assertTrue(s.toString(), s.toString().contains(expected));
      }
    }
  }
}
