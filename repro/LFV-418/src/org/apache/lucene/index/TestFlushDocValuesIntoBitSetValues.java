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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesConsumer;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

/**
 * A doc-values consumer receives, at flush, iterators over the buffered values. The {@link
 * DocIdSetIterator#intoBitSet} javadoc says the method "should behave exactly as if implemented
 * as" the loop {@code for (doc = docID(); doc < upTo; doc = nextDoc()) bitSet.set(doc - offset)},
 * so after the call the iterator is on the first document at or after {@code upTo} and its value
 * accessors describe that document, as after the loop.
 *
 * <p>This test installs a pass-through doc-values format (public codec API) whose consumer reads
 * the flush-time iterator of field "f" as: {@code nextDoc()} (doc 0), {@code intoBitSet(2)}
 * (docs 0 and 1), then the value of the current doc (doc 2) and of the next doc (doc 3). Doc d
 * holds the value 10 + d (or term "t" + d). It also runs the documented loop on a second, fresh
 * iterator as the reference.
 */
public class TestFlushDocValuesIntoBitSetValues extends LuceneTestCase {

  private static final int NUM_DOCS = 6;

  /** What the consumer observed, per field type: [docID after intoBitSet, value there, value at next doc]. */
  private final List<String> observed = new ArrayList<>();

  private final List<String> reference = new ArrayList<>();

  private interface Reader {
    String value(DocIdSetIterator it) throws IOException;
  }

  private void observe(DocIdSetIterator viaIntoBitSet, DocIdSetIterator viaLoop, Reader r)
      throws IOException {
    // bulk path
    assertEquals(0, viaIntoBitSet.nextDoc());
    FixedBitSet bits = new FixedBitSet(NUM_DOCS);
    viaIntoBitSet.intoBitSet(2, bits, 0);
    int d1 = viaIntoBitSet.docID();
    String v1 = r.value(viaIntoBitSet);
    int d2 = viaIntoBitSet.nextDoc();
    String v2 = r.value(viaIntoBitSet);
    observed.add("bits=" + bits.cardinality() + " doc=" + d1 + " value=" + v1 + " next doc=" + d2 + " value=" + v2);
    // the documented reference loop on a fresh iterator
    assertEquals(0, viaLoop.nextDoc());
    FixedBitSet bits2 = new FixedBitSet(NUM_DOCS);
    for (int doc = viaLoop.docID(); doc < 2; doc = viaLoop.nextDoc()) {
      bits2.set(doc);
    }
    int e1 = viaLoop.docID();
    String w1 = r.value(viaLoop);
    int e2 = viaLoop.nextDoc();
    String w2 = r.value(viaLoop);
    reference.add("bits=" + bits2.cardinality() + " doc=" + e1 + " value=" + w1 + " next doc=" + e2 + " value=" + w2);
  }

  private Codec observingCodec() {
    final Codec delegate = TestUtil.getDefaultCodec();
    final DocValuesFormat real = TestUtil.getDefaultDocValuesFormat();
    // Same name as the real format, so the segment is read back by the real format.
    final DocValuesFormat observing =
        new DocValuesFormat(real.getName()) {
          @Override
          public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            final DocValuesConsumer in = real.fieldsConsumer(state);
            return new DocValuesConsumer() {
              @Override
              public void addNumericField(FieldInfo field, DocValuesProducer p) throws IOException {
                if (field.name.equals("f")) {
                  observe(p.getNumeric(field), p.getNumeric(field), it -> Long.toString(((NumericDocValues) it).longValue()));
                }
                in.addNumericField(field, p);
              }

              @Override
              public void addBinaryField(FieldInfo field, DocValuesProducer p) throws IOException {
                if (field.name.equals("f")) {
                  observe(p.getBinary(field), p.getBinary(field), it -> ((BinaryDocValues) it).binaryValue().utf8ToString());
                }
                in.addBinaryField(field, p);
              }

              @Override
              public void addSortedField(FieldInfo field, DocValuesProducer p) throws IOException {
                if (field.name.equals("f")) {
                  observe(
                      p.getSorted(field),
                      p.getSorted(field),
                      it -> {
                        SortedDocValues s = (SortedDocValues) it;
                        return s.lookupOrd(s.ordValue()).utf8ToString();
                      });
                }
                in.addSortedField(field, p);
              }

              @Override
              public void addSortedNumericField(FieldInfo field, DocValuesProducer p)
                  throws IOException {
                if (field.name.equals("f")) {
                  observe(
                      p.getSortedNumeric(field),
                      p.getSortedNumeric(field),
                      it -> {
                        SortedNumericDocValues s = (SortedNumericDocValues) it;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0, c = s.docValueCount(); i < c; i++) sb.append(s.nextValue()).append(';');
                        return sb.toString();
                      });
                }
                in.addSortedNumericField(field, p);
              }

              @Override
              public void addSortedSetField(FieldInfo field, DocValuesProducer p)
                  throws IOException {
                if (field.name.equals("f")) {
                  observe(
                      p.getSortedSet(field),
                      p.getSortedSet(field),
                      it -> {
                        SortedSetDocValues s = (SortedSetDocValues) it;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0, c = s.docValueCount(); i < c; i++) {
                          sb.append(s.lookupOrd(s.nextOrd()).utf8ToString()).append(';');
                        }
                        return sb.toString();
                      });
                }
                in.addSortedSetField(field, p);
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
    return new FilterCodec(delegate.getName(), delegate) {
      final DocValuesFormat perField =
          new PerFieldDocValuesFormat() {
            @Override
            public DocValuesFormat getDocValuesFormatForField(String field) {
              return observing;
            }
          };

      @Override
      public DocValuesFormat docValuesFormat() {
        return perField;
      }
    };
  }

  private interface DocBuilder {
    void add(Document doc, int d);
  }

  private void flushAndCheck(DocBuilder builder) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(null).setCodec(observingCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int d = 0; d < NUM_DOCS; d++) {
          Document doc = new Document();
          builder.add(doc, d);
          w.addDocument(doc);
        }
        w.commit();
      }
    }
    assertEquals(1, observed.size());
    // reference loop: doc 2 then doc 3, with their own values
    assertEquals(reference.get(0), observed.get(0));
  }

  public void testNumeric() throws IOException {
    flushAndCheck((doc, d) -> doc.add(new NumericDocValuesField("f", 10 + d)));
  }

  public void testBinary() throws IOException {
    flushAndCheck((doc, d) -> doc.add(new BinaryDocValuesField("f", new BytesRef("t" + d))));
  }

  public void testSorted() throws IOException {
    flushAndCheck((doc, d) -> doc.add(new SortedDocValuesField("f", new BytesRef("t" + d))));
  }

  public void testSortedNumeric() throws IOException {
    flushAndCheck(
        (doc, d) -> {
          doc.add(new SortedNumericDocValuesField("f", 10 + d));
          doc.add(new SortedNumericDocValuesField("f", 100 + d));
        });
  }

  public void testSortedSet() throws IOException {
    flushAndCheck(
        (doc, d) -> {
          doc.add(new SortedSetDocValuesField("f", new BytesRef("t" + d)));
          doc.add(new SortedSetDocValuesField("f", new BytesRef("u" + d)));
        });
  }
}
