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
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestMergePostingsDocIdAfterExhaustion extends LuceneTestCase {

  public void testDocIdAfterExhaustionAtMerge() throws Exception {
    PostingsFormat delegate = TestUtil.getDefaultPostingsFormat();
    int[] docIdAfterExhaustion = {0};
    PostingsFormat probe =
        new PostingsFormat(delegate.getName()) {
          @Override
          public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            FieldsConsumer in = delegate.fieldsConsumer(state);
            return new FieldsConsumer() {
              @Override
              public void write(Fields fields, NormsProducer norms) throws IOException {
                if (fields instanceof MappedMultiFields) {
                  TermsEnum termsEnum = fields.terms("f").iterator();
                  termsEnum.next();
                  PostingsEnum postings = termsEnum.postings(null);
                  while (postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {}
                  docIdAfterExhaustion[0] = postings.docID();
                }
                in.write(fields, norms);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
            return delegate.fieldsProducer(state);
          }
        };

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.alwaysPostingsFormat(probe))
                    .setMergeScheduler(new SerialMergeScheduler()))) {
      for (int i = 0; i < 2; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "t", Field.Store.NO));
        w.addDocument(doc);
        w.flush();
      }
      w.forceMerge(1);
    }
    // Expected NO_MORE_DOCS (DocIdSetIterator#docID() after exhaustion); actual: -1.
    assertEquals(DocIdSetIterator.NO_MORE_DOCS, docIdAfterExhaustion[0]);
  }
}
