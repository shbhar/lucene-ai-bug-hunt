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
import org.apache.lucene.codecs.NormsConsumer;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.codecs.lucene90.IndexedDISI;
import org.apache.lucene.codecs.lucene90.Lucene90DocValuesFormat;
import org.apache.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;

/**
 * With an index sort, a numeric doc-values field (or norms) that every document has is flushed
 * through NumericDocValuesWriter.SortingNumericDocValues, whose intoBitSet sets docID to upTo. When
 * upTo is maxDoc (or larger, clamped to maxDoc), docID() then returns maxDoc instead of
 * NO_MORE_DOCS. DocIdSetIterator#intoBitSet must behave like the default loop, which ends on
 * NO_MORE_DOCS.
 */
public class TestSortingNumericDocValuesIntoBitSet extends LuceneTestCase {

  private static final int NUM_DOCS = 100;

  /** What a codec sees when it bulk-loads the flushed iterator: field -> docID() after the call. */
  private static final class Probe {
    final List<String> observations = new ArrayList<>();

    void probe(String what, NumericDocValues values, int maxDoc, int upTo) throws IOException {
      int first = values.nextDoc();
      FixedBitSet bits = new FixedBitSet(maxDoc);
      values.intoBitSet(upTo, bits, 0);
      observations.add(
          what
              + " first="
              + first
              + " upTo="
              + upTo
              + " maxDoc="
              + maxDoc
              + " cardinality="
              + bits.cardinality()
              + " docIDAfter="
              + values.docID());
      // the default implementation: for (doc = docID(); doc < upTo; doc = nextDoc()) set(doc)
      assertEquals(what + ": bits", maxDoc, bits.cardinality());
      assertEquals(
          what + ": docID() after intoBitSet(" + upTo + ") over every doc",
          DocIdSetIterator.NO_MORE_DOCS,
          values.docID());
    }
  }

  private static Codec probingCodec(Probe probe, int upTo, boolean dv, boolean normsToo) {
    Codec delegate = TestUtil.getDefaultCodec();
    DocValuesFormat lucene90 = new Lucene90DocValuesFormat();
    DocValuesFormat probing =
        new DocValuesFormat(lucene90.getName()) {
          @Override
          public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            DocValuesConsumer in = lucene90.fieldsConsumer(state);
            int maxDoc = state.segmentInfo.maxDoc();
            return new DocValuesConsumer() {
              @Override
              public void addNumericField(FieldInfo field, DocValuesProducer valuesProducer)
                  throws IOException {
                if (dv) {
                  probe.probe(
                      "dv:" + field.name, valuesProducer.getNumeric(field), maxDoc, upTo(maxDoc));
                }
                in.addNumericField(field, valuesProducer);
              }

              @Override
              public void addBinaryField(FieldInfo field, DocValuesProducer valuesProducer)
                  throws IOException {
                in.addBinaryField(field, valuesProducer);
              }

              @Override
              public void addSortedField(FieldInfo field, DocValuesProducer valuesProducer)
                  throws IOException {
                in.addSortedField(field, valuesProducer);
              }

              @Override
              public void addSortedNumericField(FieldInfo field, DocValuesProducer valuesProducer)
                  throws IOException {
                in.addSortedNumericField(field, valuesProducer);
              }

              @Override
              public void addSortedSetField(FieldInfo field, DocValuesProducer valuesProducer)
                  throws IOException {
                in.addSortedSetField(field, valuesProducer);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
            return lucene90.fieldsProducer(state);
          }

          private int upTo(int maxDoc) {
            return upTo == -1 ? maxDoc : upTo;
          }
        };
    NormsFormat norms = delegate.normsFormat();
    NormsFormat probingNorms =
        new NormsFormat() {
          @Override
          public NormsConsumer normsConsumer(SegmentWriteState state) throws IOException {
            NormsConsumer in = norms.normsConsumer(state);
            int maxDoc = state.segmentInfo.maxDoc();
            return new NormsConsumer() {
              @Override
              public void addNormsField(FieldInfo field, NormsProducer normsProducer)
                  throws IOException {
                if (normsToo) {
                  probe.probe(
                      "norms:" + field.name,
                      normsProducer.getNorms(field),
                      maxDoc,
                      upTo == -1 ? maxDoc : upTo);
                }
                in.addNormsField(field, normsProducer);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public NormsProducer normsProducer(SegmentReadState state) throws IOException {
            return norms.normsProducer(state);
          }
        };
    return new FilterCodec(delegate.getName(), delegate) {
      final DocValuesFormat perField =
          new PerFieldDocValuesFormat() {
            @Override
            public DocValuesFormat getDocValuesFormatForField(String field) {
              return probing;
            }
          };

      @Override
      public DocValuesFormat docValuesFormat() {
        return perField;
      }

      @Override
      public NormsFormat normsFormat() {
        return probingNorms;
      }
    };
  }

  private void indexAndFlush(Codec codec, boolean sorted, boolean withText) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new MockAnalyzer(random()))
              .setCodec(codec)
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
              .setRAMBufferSizeMB(64);
      if (sorted) {
        iwc.setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
      }
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("sort", (i * 37L) % NUM_DOCS));
          doc.add(new NumericDocValuesField("dense", i));
          if (withText) {
            doc.add(new TextField("body", "term" + (i % 3), Field.Store.NO));
          }
          w.addDocument(doc);
        }
        w.commit();
      }
    }
  }

  /** Index sort + a numeric field present in every document: the failing case. */
  public void testSortedDenseDocValuesUpToMaxDoc() throws IOException {
    Probe probe = new Probe();
    indexAndFlush(probingCodec(probe, -1, true, false), true, false);
  }

  /** Same with upTo = NO_MORE_DOCS (clamped to maxDoc inside). */
  public void testSortedDenseDocValuesUpToNoMoreDocs() throws IOException {
    Probe probe = new Probe();
    indexAndFlush(probingCodec(probe, DocIdSetIterator.NO_MORE_DOCS, true, false), true, false);
  }

  /** Norms of a text field that every document has go through the same class. */
  public void testSortedDenseNorms() throws IOException {
    Probe probe = new Probe();
    indexAndFlush(probingCodec(probe, -1, false, true), true, true);
  }

  /** Control: without an index sort the buffered iterator ends on NO_MORE_DOCS. */
  public void testUnsortedDenseControl() throws IOException {
    Probe probe = new Probe();
    indexAndFlush(probingCodec(probe, -1, true, true), false, true);
    assertFalse(probe.observations.isEmpty());
  }

  /**
   * Consequence: IndexedDISI.writeBitSet (public) consumes its input with intoBitSet and then
   * reads docID(); on this iterator it never sees NO_MORE_DOCS. With assertions it trips the
   * cardinality assertion of a block flush, without them it writes empty "ALL" block headers
   * until the output is exhausted. A byte cap turns the runaway into a test failure.
   */
  public void testWriteBitSetOnSortedDenseField() throws IOException {
    List<String> outcome = new ArrayList<>();
    Codec delegate = TestUtil.getDefaultCodec();
    DocValuesFormat lucene90 = new Lucene90DocValuesFormat();
    DocValuesFormat writing =
        new DocValuesFormat(lucene90.getName()) {
          @Override
          public DocValuesConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
            DocValuesConsumer in = lucene90.fieldsConsumer(state);
            return new DocValuesConsumer() {
              @Override
              public void addNumericField(FieldInfo field, DocValuesProducer valuesProducer)
                  throws IOException {
                if (field.name.equals("dense")) {
                  outcome.add(writeAndReadBack(valuesProducer.getNumeric(field)));
                }
                in.addNumericField(field, valuesProducer);
              }

              @Override
              public void addBinaryField(FieldInfo f, DocValuesProducer p) throws IOException {
                in.addBinaryField(f, p);
              }

              @Override
              public void addSortedField(FieldInfo f, DocValuesProducer p) throws IOException {
                in.addSortedField(f, p);
              }

              @Override
              public void addSortedNumericField(FieldInfo f, DocValuesProducer p)
                  throws IOException {
                in.addSortedNumericField(f, p);
              }

              @Override
              public void addSortedSetField(FieldInfo f, DocValuesProducer p) throws IOException {
                in.addSortedSetField(f, p);
              }

              @Override
              public void close() throws IOException {
                in.close();
              }
            };
          }

          @Override
          public DocValuesProducer fieldsProducer(SegmentReadState state) throws IOException {
            return lucene90.fieldsProducer(state);
          }
        };
    Codec codec =
        new FilterCodec(delegate.getName(), delegate) {
          final DocValuesFormat perField =
              new PerFieldDocValuesFormat() {
                @Override
                public DocValuesFormat getDocValuesFormatForField(String field) {
                  return writing;
                }
              };

          @Override
          public DocValuesFormat docValuesFormat() {
            return perField;
          }
        };
    indexAndFlush(codec, true, false);
    assertEquals(List.of("ok docs=" + NUM_DOCS), outcome);
  }

  private static String writeAndReadBack(NumericDocValues values) throws IOException {
    try (Directory scratch = new ByteBuffersDirectory()) {
      long length;
      short count;
      try (IndexOutput out = scratch.createOutput("disi", IOContext.DEFAULT)) {
        IndexOutput capped = new CappedOutput(out, 1 << 20);
        count = IndexedDISI.writeBitSet(values, capped, IndexedDISI.DEFAULT_DENSE_RANK_POWER);
        length = capped.getFilePointer();
      } catch (AssertionError | IllegalStateException e) {
        return "writeBitSet failed: " + e;
      }
      try (IndexInput in = scratch.openInput("disi", IOContext.DEFAULT)) {
        IndexedDISI disi =
            new IndexedDISI(
                in, 0L, length, count, IndexedDISI.DEFAULT_DENSE_RANK_POWER, NUM_DOCS);
        int n = 0;
        for (int d = disi.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = disi.nextDoc()) {
          if (d != n) {
            return "read back doc " + d + " at position " + n;
          }
          n++;
        }
        return "ok docs=" + n;
      }
    }
  }

  /** Throws once more than {@code cap} bytes were written. */
  private static final class CappedOutput extends IndexOutput {
    private final IndexOutput in;
    private final long cap;

    CappedOutput(IndexOutput in, long cap) {
      super("capped(" + in + ")", in.getName());
      this.in = in;
      this.cap = cap;
    }

    private void check() {
      if (in.getFilePointer() > cap) {
        throw new IllegalStateException("runaway output: more than " + cap + " bytes written");
      }
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    @Override
    public long getFilePointer() {
      return in.getFilePointer();
    }

    @Override
    public long getChecksum() throws IOException {
      return in.getChecksum();
    }

    @Override
    public void writeByte(byte b) throws IOException {
      in.writeByte(b);
      check();
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
      in.writeBytes(b, offset, length);
      check();
    }
  }
}
