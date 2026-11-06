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
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesConsumer;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;

public class TestDocValuesUpdateIntoBitSet extends LuceneTestCase {

  public void testIntoBitSetWithDestinationCoveringReturnedDocs() throws IOException {
    DocValuesFormat in = TestUtil.getDefaultDocValuesFormat();
    DocValuesFormat format =
        new DocValuesFormat(in.getName()) {
          @Override
          public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            DocValuesConsumer c = in.fieldsConsumer(state);
            return new DocValuesConsumer() {
              @Override
              public void addNumericField(FieldInfo field, DocValuesProducer values)
                  throws IOException {
                NumericDocValues it = values.getNumeric(field);
                int count = 0, last = -1;
                for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
                  count++;
                  last = d;
                }
                FixedBitSet bits = new FixedBitSet(last + 1);
                it = values.getNumeric(field);
                it.nextDoc();
                // expected: sets only the returned docs; actual: throws on doc 64, whose value the update removed
                it.intoBitSet(DocIdSetIterator.NO_MORE_DOCS, bits, 0);
                assertEquals(count, bits.cardinality());
                c.addNumericField(field, values);
              }

              @Override
              public void addBinaryField(FieldInfo f, DocValuesProducer v) throws IOException {
                c.addBinaryField(f, v);
              }

              @Override
              public void addSortedField(FieldInfo f, DocValuesProducer v) throws IOException {
                c.addSortedField(f, v);
              }

              @Override
              public void addSortedNumericField(FieldInfo f, DocValuesProducer v)
                  throws IOException {
                c.addSortedNumericField(f, v);
              }

              @Override
              public void addSortedSetField(FieldInfo f, DocValuesProducer v) throws IOException {
                c.addSortedSetField(f, v);
              }

              @Override
              public void close() throws IOException {
                c.close();
              }
            };
          }

          @Override
          public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
            return in.fieldsProducer(state);
          }
        };
    Codec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          @Override
          public DocValuesFormat docValuesFormat() {
            return format;
          }
        };

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      for (int i = 0; i < 65; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        if (i == 0 || i == 64) {
          doc.add(new NumericDocValuesField("n", i));
        }
        w.addDocument(doc);
      }
      w.commit();
      w.updateDocValues(new Term("id", "64"), new NumericDocValuesField("n", (Long) null));
      w.commit();
    }
  }
}
