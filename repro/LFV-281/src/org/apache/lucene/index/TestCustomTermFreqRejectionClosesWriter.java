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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A document that combines a custom term frequency (TermFrequencyAttribute != 1) with term vector
 * positions or offsets, or with postings positions, is rejected with an exception, as
 * TestCustomTermFreq expects. The rejection must only reject that document: the IndexWriter must
 * stay open and keep the documents added before it.
 */
public class TestCustomTermFreqRejectionClosesWriter extends LuceneTestCase {

  /** Two tokens; the second one carries term frequency 2. */
  private static final class TwoTokens extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    private int upto;

    @Override
    public boolean incrementToken() {
      if (upto == 2) {
        return false;
      }
      clearAttributes();
      termAtt.append(upto == 0 ? "foo" : "bar");
      freqAtt.setTermFrequency(upto == 0 ? 1 : 2);
      upto++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
    }
  }

  private static Document idDoc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    return doc;
  }

  private void checkRejectedDocumentKeepsWriterUsable(FieldType badType) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
      w.addDocument(idDoc("committed"));
      w.commit();
      w.addDocument(idDoc("uncommitted"));

      Document bad = idDoc("bad");
      bad.add(new Field("body", new TwoTokens(), badType));
      // the document is rejected, as documented by TestCustomTermFreq
      expectThrows(RuntimeException.class, () -> w.addDocument(bad));

      Throwable tragic = w.getTragicException();
      String next;
      try {
        w.addDocument(idDoc("after"));
        w.commit();
        next = "ok";
      } catch (AlreadyClosedException e) {
        next = "AlreadyClosedException";
      }
      int committedDocs;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committedDocs = r.numDocs();
      }
      w.close();
      assertEquals(
          "a rejected document must not close the writer",
          "tragic=null, next addDocument+commit=ok, committed docs=3",
          "tragic=" + tragic + ", next addDocument+commit=" + next + ", committed docs=" + committedDocs);
    }
  }

  public void testTermVectorPositions() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.freeze();
    checkRejectedDocumentKeepsWriterUsable(ft);
  }

  public void testTermVectorOffsets() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorOffsets(true);
    ft.freeze();
    checkRejectedDocumentKeepsWriterUsable(ft);
  }

  public void testPostingsPositions() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED); // DOCS_AND_FREQS_AND_POSITIONS
    ft.freeze();
    checkRejectedDocumentKeepsWriterUsable(ft);
  }

  /** Control: an input error the indexing chain checks before inverting keeps the writer open. */
  public void testControlOffsetsGoingBackwards() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
      w.addDocument(idDoc("committed"));
      w.commit();
      w.addDocument(idDoc("uncommitted"));
      Document bad = idDoc("bad");
      FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
      ft.setStoreTermVectors(true);
      ft.setStoreTermVectorOffsets(true);
      bad.add(
          new Field(
              "body",
              new org.apache.lucene.tests.analysis.CannedTokenStream(
                  new org.apache.lucene.tests.analysis.Token("foo", 5, 6),
                  new org.apache.lucene.tests.analysis.Token("bar", 1, 2)),
              ft));
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(bad));
      assertNull(w.getTragicException());
      w.addDocument(idDoc("after"));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(3, r.numDocs());
      }
      w.close();
    }
  }
}
