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
 * <p>Term "t": docs 0..8191 (one full level-1 block), then 7,980 consecutive docs ending at
 * 2147483435 (31 full 256-doc blocks and 44 tail docs, no second level-1 block). The 31st tail
 * block is dense and ends at doc 2147483391 = NO_MORE_DOCS - 256.
 */
public class TestDocIDRunEndNearMaxDoc extends LuceneTestCase {

  private static final int TAIL_START = 2147483391 - 31 * 256 + 1; // 2147475456
  private static final int TAIL_END = 2147483435; // inclusive, < IndexWriter.MAX_DOCS

  private static int[] docs() {
    int n = 8192 + (TAIL_END - TAIL_START + 1);
    int[] docs = new int[n];
    int k = 0;
    for (int d = 0; d < 8192; d++) {
      docs[k++] = d;
    }
    for (long d = TAIL_START; d <= TAIL_END; d++) {
      docs[k++] = (int) d;
    }
    return docs;
  }

  public void testDocIDRunEndInLastLevel1Tail() throws IOException {
    assertTrue(TAIL_END < IndexWriter.MAX_DOCS);
    final int[] docs = docs();
    assertEquals(8192 + 31 * 256 + 44, docs.length);
    try (Directory dir = new ByteBuffersDirectory()) {
      Codec codec = Codec.getDefault();
      SegmentInfo si =
          new SegmentInfo(
              dir,
              Version.LATEST,
              Version.LATEST,
              "_0",
              IndexWriter.MAX_DOCS,
              false,
              false,
              codec,
              Collections.emptyMap(),
              StringHelper.randomId(),
              new HashMap<>(),
              null);
      FieldInfo fi =
          new FieldInfo(
              "f", 0, false, true, false, IndexOptions.DOCS, DocValuesType.NONE,
              DocValuesSkipIndexType.NONE, -1, Map.of(), 0, 0, 0, 0, VectorEncoding.FLOAT32,
              VectorSimilarityFunction.EUCLIDEAN, false, false);
      FieldInfos fieldInfos = new FieldInfos(new FieldInfo[] {fi});
      PostingsFormat format = new Lucene104PostingsFormat();
      SegmentWriteState writeState =
          new SegmentWriteState(InfoStream.NO_OUTPUT, dir, si, fieldInfos, null, IOContext.DEFAULT);
      try (FieldsConsumer consumer = format.fieldsConsumer(writeState)) {
        consumer.write(new OneTermFields(docs), null);
      }
      try (FieldsProducer producer =
          format.fieldsProducer(new SegmentReadState(dir, si, fieldInfos, IOContext.DEFAULT))) {
        TermsEnum te = producer.terms("f").iterator();
        assertTrue(te.seekExact(new BytesRef("t")));
        assertEquals(docs.length, te.docFreq());
        PostingsEnum pe = te.postings(null, PostingsEnum.NONE);
        int target = 2147483391 - 255; // first doc of the 31st tail block
        assertEquals(target, pe.advance(target));
        int runEnd = pe.docIDRunEnd();
        // DocIdSetIterator#docIDRunEnd: greater than docID(), and every doc in [docID(), runEnd)
        // matches; here the docs match up to TAIL_END inclusive.
        assertTrue("docIDRunEnd()=" + runEnd + " for docID()=" + pe.docID(), runEnd > pe.docID());
        assertTrue("docIDRunEnd()=" + runEnd, runEnd <= TAIL_END + 1);
        assertEquals(target, pe.docID());
      }
    }
  }

  /** Fields with a single field "f" and a single term "t" (DOCS only). */
  private static final class OneTermFields extends Fields {
    private final int[] docs;

    OneTermFields(int[] docs) {
      this.docs = docs;
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
          return new OneTermEnum(docs);
        }

        @Override
        public long size() {
          return 1;
        }

        @Override
        public long getSumTotalTermFreq() {
          return docs.length;
        }

        @Override
        public long getSumDocFreq() {
          return docs.length;
        }

        @Override
        public int getDocCount() {
          return docs.length;
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
    private final int[] docs;
    private boolean done;

    OneTermEnum(int[] docs) {
      this.docs = docs;
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
      return docs.length;
    }

    @Override
    public long totalTermFreq() {
      return docs.length;
    }

    @Override
    public PostingsEnum postings(PostingsEnum reuse, int flags) {
      return new PostingsEnum() {
        int i = -1;

        @Override
        public int docID() {
          return i < 0 ? -1 : i >= docs.length ? DocIdSetIterator.NO_MORE_DOCS : docs[i];
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
          return docs.length;
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
