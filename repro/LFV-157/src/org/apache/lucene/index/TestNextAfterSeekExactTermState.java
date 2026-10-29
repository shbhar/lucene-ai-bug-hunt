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
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestNextAfterSeekExactTermState extends LuceneTestCase {

  public void testNextAfterSeekExactWithTermStateOnNewEnum() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (String s : new String[] {"a", "b", "c"}) {
        Document doc = new Document();
        doc.add(new StringField("f", s, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReaderContext leaf = reader.leaves().get(0);
        Term term = new Term("f", "b");
        TermState state = TermStates.build(new IndexSearcher(reader), term, false).get(leaf).get();
        TermsEnum te = leaf.reader().terms("f").iterator();
        te.seekExact(term.bytes(), state);
        // expected: next() after the seek to "b" returns "c"; actual: "a", the field's first term
        assertEquals("c", te.next().utf8ToString());
      }
    }
  }
}
