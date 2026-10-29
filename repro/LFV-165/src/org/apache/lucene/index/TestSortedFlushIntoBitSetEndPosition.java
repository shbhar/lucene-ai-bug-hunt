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
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.NormsConsumer;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;

public class TestSortedFlushIntoBitSetEndPosition extends LuceneTestCase {

  public void testIntoBitSetUpToMaxDocEndsOnNoMoreDocs() throws IOException {
    int[] docIDAfter = {-1};
    FilterCodec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          @Override
          public NormsFormat normsFormat() {
            NormsFormat in = delegate.normsFormat();
            return new NormsFormat() {
              @Override
              public NormsConsumer normsConsumer(SegmentWriteState state) throws IOException {
                NormsConsumer consumer = in.normsConsumer(state);
                int maxDoc = state.segmentInfo.maxDoc();
                return new NormsConsumer() {
                  @Override
                  public void addNormsField(FieldInfo field, NormsProducer producer)
                      throws IOException {
                    NumericDocValues norms = producer.getNorms(field);
                    norms.nextDoc();
                    norms.intoBitSet(maxDoc, new FixedBitSet(maxDoc), 0);
                    docIDAfter[0] = norms.docID();
                    consumer.addNormsField(field, producer);
                  }

                  @Override
                  public void close() throws IOException {
                    consumer.close();
                  }
                };
              }

              @Override
              public NormsProducer normsProducer(SegmentReadState state) throws IOException {
                return in.normsProducer(state);
              }
            };
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(codec)
            .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 2; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("sort", -i));
        doc.add(new TextField("body", "x", Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
    }
    // Expected: intoBitSet(maxDoc) over every doc ends on NO_MORE_DOCS; actual: docID() == maxDoc.
    assertEquals("docID() after intoBitSet(maxDoc)", DocIdSetIterator.NO_MORE_DOCS, docIDAfter[0]);
  }
}
