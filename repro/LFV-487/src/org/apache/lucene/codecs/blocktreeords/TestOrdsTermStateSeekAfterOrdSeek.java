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
package org.apache.lucene.codecs.blocktreeords;

import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestOrdsTermStateSeekAfterOrdSeek extends LuceneTestCase {

  public void testSeekExactAfterOrdSeekAndSameTermStateSeek() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.alwaysPostingsFormat(new BlockTreeOrdsPostingsFormat()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        for (int i = 0; i < 25; i++) {
          doc.add(new StringField("f", String.format(Locale.ROOT, "a%02d", i), Field.Store.NO));
        }
        doc.add(new StringField("f", "b", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        TermsEnum te = getOnlyLeafReader(reader).terms("f").iterator();
        te.seekExact(0L);
        te.seekExact(BytesRef.deepCopyOf(te.term()), te.termState());
        // expected true ("b" exists; a00..a24 form a sub-block), actual false
        assertTrue(
            "existing term b: expected seekExact true, got false", te.seekExact(new BytesRef("b")));
      }
    }
  }
}
