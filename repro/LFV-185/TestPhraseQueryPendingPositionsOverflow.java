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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPhraseQueryPendingPositionsOverflow extends LuceneTestCase {

  public void testSkippedFreqsPastIntMax() throws IOException {
    try (Directory dir = FSDirectory.open(createTempDir())) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new TextField("f", repeatT(1 << 30)));
          w.addDocument(doc);
        }
        Document doc = new Document();
        doc.add(new TextField("f", "v t", Field.Store.NO));
        w.addDocument(doc);
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(
            "hits of f:\"v t\" (only the last doc matches; the 3 freqs of t sum to 2^31 + 1)",
            1,
            new IndexSearcher(reader).count(new PhraseQuery("f", "v", "t")));
      }
    }
  }

  private static TokenStream repeatT(int count) {
    return new TokenStream() {
      private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
      private int left = count;

      @Override
      public boolean incrementToken() {
        if (left-- == 0) {
          return false;
        }
        clearAttributes();
        term.append('t');
        return true;
      }
    };
  }
}
