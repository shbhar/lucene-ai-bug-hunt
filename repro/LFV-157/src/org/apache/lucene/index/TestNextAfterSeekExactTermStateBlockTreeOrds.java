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

import org.apache.lucene.codecs.blocktreeords.BlockTreeOrdsPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Same sequence as TestNextAfterSeekExactTermState on the block-tree-with-ords terms dictionary of
 * lucene/codecs (BlockTreeOrdsPostingsFormat), whose OrdsSegmentTermsEnum has the same next() logic.
 */
public class TestNextAfterSeekExactTermStateBlockTreeOrds extends LuceneTestCase {

  public void testNextAfterSeekExactWithTermStateOnNewEnum() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setCodec(TestUtil.alwaysPostingsFormat(new BlockTreeOrdsPostingsFormat()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String s : new String[] {"apple", "banana", "cherry"}) {
          Document doc = new Document();
          doc.add(new StringField("f", s, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Terms terms = reader.leaves().get(0).reader().terms("f");
        TermsEnum source = terms.iterator();
        assertTrue(source.seekExact(new BytesRef("banana")));
        TermState state = source.termState();
        TermsEnum te = terms.iterator();
        te.seekExact(new BytesRef("banana"), state);
        assertEquals(new BytesRef("banana"), te.term());
        assertEquals(new BytesRef("cherry"), te.next());
      }
    }
  }
}
