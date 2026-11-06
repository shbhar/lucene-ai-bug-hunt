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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestSortingPostingsEnumReusedPayload extends LuceneTestCase {

  public void testReusedEnumHasNoPayloadOnFieldWithoutPositions() throws Exception {
    FieldType docsAndFreqs = new FieldType(StringField.TYPE_NOT_STORED);
    docsAndFreqs.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 2; i++) {
        Token token = new Token("a", 0, 1);
        token.setPayload(new BytesRef("xyz"));
        Document doc = new Document();
        doc.add(new NumericDocValuesField("id", i));
        doc.add(new TextField("p", new CannedTokenStream(token)));
        doc.add(new Field("q", "b", docsAndFreqs));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        CodecReader sorted =
            SortingCodecReader.wrap(
                (CodecReader) getOnlyLeafReader(r),
                new Sort(new SortField("id", SortField.Type.LONG, true)));
        TermsEnum te = sorted.terms("p").iterator();
        te.seekExact(new BytesRef("a"));
        PostingsEnum pe = te.postings(null, PostingsEnum.ALL);
        pe.nextDoc();
        pe.nextPosition(); // p:a, payload "xyz"
        te = sorted.terms("q").iterator();
        te.seekExact(new BytesRef("b"));
        pe = te.postings(pe, PostingsEnum.ALL); // reuse the enum for q (DOCS_AND_FREQS)
        pe.nextDoc();
        assertEquals(-1, pe.nextPosition());
        // expected null (q indexes no payloads), actual "xyz" (the payload last read from p)
        assertNull(pe.getPayload());
      }
    }
  }
}
