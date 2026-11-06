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
package org.apache.lucene.codecs.lucene104;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

public class TestBlockPostingsIntoBitSetShortDest extends LuceneTestCase {

  public void testBitSetBlockIntoShorterBitSet() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < 512; i++) {
          Document doc = new Document();
          if (i % 2 == 0) {
            doc.add(new StringField("f", "t", Field.Store.NO));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        TermsEnum te = getOnlyLeafReader(reader).terms("f").iterator();
        assertTrue(te.seekExact(new BytesRef("t")));
        PostingsEnum pe = te.postings(null, PostingsEnum.NONE);
        assertEquals(0, pe.nextDoc());
        FixedBitSet bits = new FixedBitSet(1);
        // Expected: only doc 0 is below upTo=2, so bit 0 is set; actual: IndexOutOfBoundsException.
        pe.intoBitSet(2, bits, 0);
        assertTrue(bits.get(0));
        assertEquals(2, pe.docID());
      }
    }
  }
}
