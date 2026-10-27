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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/** docIDRunEnd() after advanceShallow must still describe the run that contains docID(). */
public class TestAdvanceShallowDocIDRunEnd extends LuceneTestCase {

  public void testDocIDRunEnd() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 6000; i++) {
          // docs 0..255 all contain "t" (one dense block), then every third doc
          boolean has = i < 256 || i % 3 == 0;
          Document doc = new Document();
          doc.add(new TextField("f", has ? "t x" : "x", Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        TermsEnum te = reader.leaves().get(0).reader().terms("f").iterator();
        assertTrue(te.seekExact(new BytesRef("t")));
        ImpactsEnum e = te.impacts(PostingsEnum.FREQS);
        assertEquals(0, e.nextDoc());
        assertEquals(256, e.docIDRunEnd()); // docs 0..255 match, 256 does not
        e.advanceShallow(3000);
        assertEquals(0, e.docID());
        assertEquals(256, e.docIDRunEnd());
      }
    }
  }
}
