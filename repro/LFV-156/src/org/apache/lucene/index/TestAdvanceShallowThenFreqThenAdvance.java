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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestAdvanceShallowThenFreqThenAdvance extends LuceneTestCase {

  public void testAdvanceAfterFreq() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 1200; i++) {
        Document doc = new Document();
        doc.add(new TextField("f", "t ".repeat(1 + i % 5), Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        TermsEnum termsEnum = getOnlyLeafReader(reader).terms("f").iterator();
        assertTrue(termsEnum.seekExact(new BytesRef("t")));
        ImpactsEnum impacts = termsEnum.impacts(PostingsEnum.FREQS);
        assertEquals(0, impacts.nextDoc());
        impacts.advanceShallow(256);
        impacts.freq();
        // every doc has t, and the advanceShallow javadoc allows advance(256): expected 256, got 261
        assertEquals("advance(256) after freq()", 256, impacts.advance(256));
      }
    }
  }
}
