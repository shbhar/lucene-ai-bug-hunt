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
import org.apache.lucene.codecs.DocValuesConsumer;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;

public class TestFlushNumericDocValuesIntoBitSet extends LuceneTestCase {

  private String observed;

  public void testValueAfterIntoBitSet() throws IOException {
    DocValuesFormat real = TestUtil.getDefaultDocValuesFormat();
    DocValuesFormat observing =
        new DocValuesFormat(real.getName()) {
          @Override
          public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            DocValuesConsumer in = real.fieldsConsumer(state);
            return new DocValuesConsumer() {
              @Override
              public void addNumericField(FieldInfo field, DocValuesProducer producer)
                  throws IOException {
                NumericDocValues values = producer.getNumeric(field);
                values.nextDoc();
                values.intoBitSet(2, new FixedBitSet(3), 0);
                observed = "doc=" + values.docID() + " value=" + values.longValue();
                in.addNumericField(field, producer);
              }

              @Override
              public void addBinaryField(FieldInfo field, DocValuesProducer producer)
                  throws IOException {
                in.addBinaryField(field, producer);
              }

              @Override
              public void addSortedField(FieldInfo field, DocValuesProducer producer)
                  throws IOException {
                in.addSortedField(field, producer);
              }

              @Override
              public void addSortedNumericField(FieldInfo field, DocValuesProducer producer)
                  throws IOException {
                in.addSortedNumericField(field, producer);
              }

              @Override
              public void addSortedSetField(FieldInfo field, DocValuesProducer producer)
                  throws IOException {
                in.addSortedSetField(field, producer);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
            return real.fieldsProducer(state);
          }
        };

    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.alwaysDocValuesFormat(observing));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int d = 0; d < 3; d++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("f", 10 + d));
        w.addDocument(doc);
      }
    }

    // as after the documented nextDoc() loop, doc 2 is current with value 12; actual: doc 0's 10
    assertEquals("doc=2 value=12", observed);
  }
}
