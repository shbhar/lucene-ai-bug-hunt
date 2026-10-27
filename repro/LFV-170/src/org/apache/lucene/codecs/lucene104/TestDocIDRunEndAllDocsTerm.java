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
package org.apache.lucene.codecs.lucene104;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.DocValuesSkipIndexType;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

/**
 * Codec-level check of Lucene104 BlockPostingsEnum#docIDRunEnd near the top of the doc ID space.
 * The postings are written through the public PostingsFormat API into a segment whose maxDoc is
 * IndexWriter.MAX_DOCS, as an IndexWriter segment of that size would write them; only the doc IDs
 * of one term are materialized.
 *
 * <p>Term "t" is in every document of a 2,147,475,712-doc segment (262,143 complete level-1 blocks
 * plus one 256-doc block); the loop is CheckIndex#checkDocIDRuns from the last level-1 block on.
 */
public class TestDocIDRunEndAllDocsTerm extends LuceneTestCase {

  // 262,143 complete level-1 blocks (8,192 docs each) plus one full 256-doc block in the tail
  private static final int N = 262143 * 8192 + 256; // 2,147,475,712 docs, all containing "t"

  public void testAllDocsTermCheckIndexStyle() throws IOException {
    assertTrue(N <= IndexWriter.MAX_DOCS);
    try (Directory dir = new ByteBuffersDirectory()) {
      Codec codec = Codec.getDefault();
      SegmentInfo si =
          new SegmentInfo(dir, Version.LATEST, Version.LATEST, "_0", N, false, false, codec,
              Collections.emptyMap(), StringHelper.randomId(), new HashMap<>(), null);
      FieldInfo fi =
          new FieldInfo(
              "f", 0, false, true, false, IndexOptions.DOCS, DocValuesType.NONE,
              DocValuesSkipIndexType.NONE, -1, Map.of(), 0, 0, 0, 0, VectorEncoding.FLOAT32,
              VectorSimilarityFunction.EUCLIDEAN, false, false);
      FieldInfos fieldInfos = new FieldInfos(new FieldInfo[] {fi});
      PostingsFormat format = new Lucene104PostingsFormat();
      SegmentWriteState writeState =
          new SegmentWriteState(InfoStream.NO_OUTPUT, dir, si, fieldInfos, null, IOContext.DEFAULT);
      long t0 = System.nanoTime();
      try (FieldsConsumer consumer = format.fieldsConsumer(writeState)) {
        consumer.write(new OneTermFields(N), null);
      }
      System.out.println("wrote " + N + " postings in " + (System.nanoTime() - t0) / 1000000 + " ms");
      try (FieldsProducer producer =
          format.fieldsProducer(new SegmentReadState(dir, si, fieldInfos, IOContext.DEFAULT))) {
        TermsEnum te = producer.terms("f").iterator();
        assertTrue(te.seekExact(new BytesRef("t")));
        assertEquals(N, te.docFreq());
        // the same loop as CheckIndex#checkDocIDRuns, from the last level-1 block on
        PostingsEnum pe = te.postings(null, PostingsEnum.NONE);
        int start = 262143 * 8192 - 1;
        assertEquals(start, pe.advance(start));
        for (int doc = pe.docID(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = pe.nextDoc()) {
          int runEnd = pe.docIDRunEnd();
          assertTrue("docIDRunEnd()=" + runEnd + " <= doc " + doc, runEnd > doc);
          assertTrue("docIDRunEnd()=" + runEnd + " > " + N, runEnd <= N);
        }
      }
    }
  }

  /** Fields with a single field "f" and a single term "t" (DOCS only). */
  private static final class OneTermFields extends Fields {
    private final int n;

    OneTermFields(int n) {
      this.n = n;
    }

    @Override
    public Iterator<String> iterator() {
      return List.of("f").iterator();
    }

    @Override
    public Terms terms(String field) {
      return new Terms() {
        @Override
        public TermsEnum iterator() {
          return new OneTermEnum(n);
        }

        @Override
        public long size() {
          return 1;
        }

        @Override
        public long getSumTotalTermFreq() {
          return n;
        }

        @Override
        public long getSumDocFreq() {
          return n;
        }

        @Override
        public int getDocCount() {
          return n;
        }

        @Override
        public boolean hasFreqs() {
          return false;
        }

        @Override
        public boolean hasOffsets() {
          return false;
        }

        @Override
        public boolean hasPositions() {
          return false;
        }

        @Override
        public boolean hasPayloads() {
          return false;
        }
      };
    }

    @Override
    public int size() {
      return 1;
    }
  }

  private static final class OneTermEnum extends org.apache.lucene.index.BaseTermsEnum {
    private final int n;
    private boolean done;

    OneTermEnum(int n) {
      this.n = n;
    }

    @Override
    public BytesRef next() {
      if (done) {
        return null;
      }
      done = true;
      return new BytesRef("t");
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void seekExact(long ord) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BytesRef term() {
      return new BytesRef("t");
    }

    @Override
    public long ord() {
      return 0;
    }

    @Override
    public int docFreq() {
      return n;
    }

    @Override
    public long totalTermFreq() {
      return n;
    }

    @Override
    public PostingsEnum postings(PostingsEnum reuse, int flags) {
      return new PostingsEnum() {
        int i = -1;

        @Override
        public int docID() {
          return i < 0 ? -1 : i >= n ? DocIdSetIterator.NO_MORE_DOCS : i;
        }

        @Override
        public int nextDoc() {
          i++;
          return docID();
        }

        @Override
        public int advance(int target) throws IOException {
          return slowAdvance(target);
        }

        @Override
        public long cost() {
          return n;
        }

        @Override
        public int freq() {
          return 1;
        }

        @Override
        public int nextPosition() {
          return -1;
        }

        @Override
        public int startOffset() {
          return -1;
        }

        @Override
        public int endOffset() {
          return -1;
        }

        @Override
        public BytesRef getPayload() {
          return null;
        }
      };
    }

    @Override
    public org.apache.lucene.index.ImpactsEnum impacts(int flags) {
      throw new UnsupportedOperationException();
    }
  }
}
