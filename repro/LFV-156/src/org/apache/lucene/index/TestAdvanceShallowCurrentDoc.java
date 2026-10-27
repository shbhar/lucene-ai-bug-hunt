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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * ImpactsSource#advanceShallow(target) only restricts later nextDoc()/advance() calls; the enum
 * stays on its current document. Reading that document's freq or positions afterwards should give
 * that document's values and must not break a later legal advance(target).
 */
public class TestAdvanceShallowCurrentDoc extends LuceneTestCase {

  // Doc i contains "t" iff i % 3 == 0, after (i % 7) filler tokens, (1 + i % 3) times.
  private static Directory buildIndex() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 6000; i++) {
        StringBuilder sb = new StringBuilder();
        if (i % 3 == 0) {
          for (int k = 0; k < i % 7; k++) {
            sb.append("y ");
          }
          for (int k = 0; k < 1 + i % 3; k++) {
            sb.append("t x ");
          }
        } else {
          sb.append("x");
        }
        Document doc = new Document();
        doc.add(new TextField("f", sb.toString(), Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static TermsEnum termsEnum(DirectoryReader reader) throws Exception {
    TermsEnum te = reader.leaves().get(0).reader().terms("f").iterator();
    assertTrue(te.seekExact(new BytesRef("t")));
    return te;
  }

  /** freq() of the current doc after advanceShallow, then a legal advance. */
  public void testFreqThenAdvance() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      ImpactsEnum e = termsEnum(reader).impacts(PostingsEnum.FREQS);
      assertEquals(0, e.nextDoc());
      e.advanceShallow(3000);
      assertEquals(0, e.docID());
      assertEquals(1, e.freq()); // doc 0 has freq 1
      assertEquals(3000, e.advance(3000));
      assertEquals(1, e.freq()); // doc 3000 has freq 1
      for (int expected = 3003; expected < 6000; expected += 3) {
        assertEquals(expected, e.nextDoc());
        assertEquals(1 + expected % 3, e.freq());
      }
      assertEquals(DocIdSetIterator.NO_MORE_DOCS, e.nextDoc());
    }
  }

  /** nextPosition() of the current doc after advanceShallow (freq already decoded). */
  public void testPositionsOfCurrentDoc() throws Exception {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      ImpactsEnum e = termsEnum(reader).impacts(PostingsEnum.POSITIONS);
      assertEquals(0, e.nextDoc());
      assertEquals(3, e.nextDoc());
      assertEquals(1, e.freq());
      e.advanceShallow(3000);
      assertEquals(3, e.docID());
      assertEquals(1, e.freq());
      assertEquals(3, e.nextPosition()); // doc 3: three filler tokens, then "t"
      assertEquals(3000, e.advance(3000));
      assertEquals(1, e.freq());
      assertEquals(3000 % 7, e.nextPosition());
    }
  }
}
