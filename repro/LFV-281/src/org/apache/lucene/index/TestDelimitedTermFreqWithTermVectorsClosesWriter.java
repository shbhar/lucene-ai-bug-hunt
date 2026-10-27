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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.DelimitedTermFrequencyTokenFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The same defect reached through a shipped analysis component: a field configured as the
 * DelimitedTermFrequencyTokenFilter javadoc asks (DOCS_AND_FREQS, no positions or offsets in the
 * postings) that also stores term vectors with positions, e.g. for highlighting. Documents without
 * a "|N" suffix index fine; the first document whose text contains "word|2" is rejected (as
 * expected) and, in addition, closes the IndexWriter and drops every uncommitted document.
 */
public class TestDelimitedTermFreqWithTermVectorsClosesWriter extends LuceneTestCase {

  public void testOneDocumentWithACustomFrequencyClosesTheWriter() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new DelimitedTermFrequencyTokenFilter(t));
          }
        };
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.freeze();
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a));
      for (int i = 0; i < 10; i++) {
        Document d = new Document();
        d.add(new Field("body", "plain text number " + i, ft));
        w.addDocument(d);
      }
      w.commit();
      for (int i = 0; i < 10; i++) {
        Document d = new Document();
        d.add(new Field("body", "more text " + i, ft));
        w.addDocument(d); // 10 more documents, not committed yet
      }
      Document weighted = new Document();
      weighted.add(new Field("body", "boosted|2 text", ft));
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(weighted));

      Throwable tragic = w.getTragicException();
      String next;
      try {
        w.commit();
        next = "ok";
      } catch (AlreadyClosedException e) {
        next = "AlreadyClosedException";
      }
      int docs;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        docs = r.numDocs();
      }
      w.close();
      assertEquals(
          "tragic=null, commit=ok, committed docs=20",
          "tragic=" + tragic + ", commit=" + next + ", committed docs=" + docs);
    }
  }
}
