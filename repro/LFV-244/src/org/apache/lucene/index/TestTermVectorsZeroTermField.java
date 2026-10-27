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
import java.io.UncheckedIOException;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

/**
 * addIndexes(CodecReader) through a reader that removes some terms of one field, consistently from
 * the postings, the term vectors and the norms (for example to drop unwanted terms while copying an
 * index); CheckIndex accepts that reader (control test). A document whose field held only removed
 * terms then has, in its term vectors, a field with zero terms, which the default merge copier
 * (TermVectorsWriter#addAllDocVectors) hands to the codec. The postings writer simply drops such a
 * field; the term-vectors output must stay readable too.
 */
public class TestTermVectorsZeroTermField extends LuceneTestCase {

  private static final String FIELD = "a";
  private static final Set<BytesRef> DROPPED = Set.of(new BytesRef("alpha"), new BytesRef("beta"));

  /** Skips the dropped terms; supports next, seekCeil and seekExact. */
  private static final class DroppingTermsEnum extends FilterLeafReader.FilterTermsEnum {
    DroppingTermsEnum(TermsEnum in) {
      super(in);
    }

    @Override
    public BytesRef next() throws IOException {
      BytesRef t = in.next();
      while (t != null && DROPPED.contains(t)) {
        t = in.next();
      }
      return t;
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) throws IOException {
      SeekStatus s = in.seekCeil(text);
      if (s == SeekStatus.END || DROPPED.contains(in.term()) == false) {
        return s;
      }
      return next() == null ? SeekStatus.END : SeekStatus.NOT_FOUND;
    }

    @Override
    public boolean seekExact(BytesRef text) throws IOException {
      return DROPPED.contains(text) == false && in.seekExact(text);
    }

    @Override
    public void seekExact(BytesRef term, TermState state) throws IOException {
      if (DROPPED.contains(term)) {
        throw new IllegalArgumentException("dropped term");
      }
      in.seekExact(term, state);
    }

    @Override
    public void seekExact(long ord) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long ord() {
      throw new UnsupportedOperationException();
    }
  }

  /** Terms of FIELD without the dropped terms, with recomputed statistics. */
  private static Terms dropping(Terms t, boolean isTermVector) {
    if (t == null) {
      return null;
    }
    return new FilterLeafReader.FilterTerms(t) {
      @Override
      public TermsEnum iterator() throws IOException {
        return new DroppingTermsEnum(in.iterator());
      }

      private long[] stats() {
        try {
          long numTerms = 0, sumDocFreq = 0, sumTtf = 0;
          FixedBitSet docs = new FixedBitSet(1024);
          TermsEnum te = iterator();
          PostingsEnum pe = null;
          for (BytesRef b = te.next(); b != null; b = te.next()) {
            numTerms++;
            sumDocFreq += te.docFreq();
            sumTtf += te.totalTermFreq();
            pe = te.postings(pe, PostingsEnum.NONE);
            for (int d = pe.nextDoc(); d != PostingsEnum.NO_MORE_DOCS; d = pe.nextDoc()) {
              docs.set(d);
            }
          }
          return new long[] {numTerms, sumDocFreq, sumTtf, docs.cardinality()};
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public long size() {
        return isTermVector ? -1 : stats()[0]; // a term-vector filter often cannot tell cheaply
      }

      @Override
      public long getSumDocFreq() {
        return stats()[1];
      }

      @Override
      public long getSumTotalTermFreq() {
        return stats()[2];
      }

      @Override
      public int getDocCount() {
        return (int) stats()[3];
      }
    };
  }

  /** Removes DROPPED from FIELD in postings, term vectors and norms. */
  private static final class DropTerms extends FilterLeafReader {
    DropTerms(LeafReader in) {
      super(in);
    }

    @Override
    public Terms terms(String name) {
      Terms t;
      try {
        t = super.terms(name); // declares IOException before Lucene 11
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return name.equals(FIELD) ? dropping(t, false) : t;
    }

    @Override
    public NumericDocValues getNormValues(String name) throws IOException {
      NumericDocValues norms = super.getNormValues(name);
      if (norms == null || name.equals(FIELD) == false) {
        return norms;
      }
      final Terms terms = terms(FIELD);
      final FixedBitSet hasTerms = new FixedBitSet(maxDoc());
      TermsEnum te = terms.iterator();
      PostingsEnum pe = null;
      for (BytesRef b = te.next(); b != null; b = te.next()) {
        pe = te.postings(pe, PostingsEnum.NONE);
        for (int d = pe.nextDoc(); d != PostingsEnum.NO_MORE_DOCS; d = pe.nextDoc()) {
          hasTerms.set(d);
        }
      }
      return new FilterNumericDocValues(norms) {
        @Override
        public long longValue() throws IOException {
          return hasTerms.get(docID()) ? in.longValue() : 0; // field emptied: norm 0
        }
      };
    }

    @Override
    public TermVectors termVectors() throws IOException {
      final TermVectors tv = in.termVectors();
      return new TermVectors() {
        @Override
        public Fields get(int doc) throws IOException {
          Fields f = tv.get(doc);
          if (f == null) {
            return null;
          }
          return new FilterFields(f) {
            @Override
            public Terms terms(String name) {
              Terms t;
              try {
                t = in.terms(name); // declares IOException before Lucene 11
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              return name.equals(FIELD) ? dropping(t, true) : t;
            }
          };
        }
      };
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  private static String dump(Fields f) throws IOException {
    if (f == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    for (String fn : f) {
      Terms t = f.terms(fn);
      if (t == null) {
        continue;
      }
      sb.append(fn).append("=[");
      TermsEnum te = t.iterator();
      for (BytesRef b = te.next(); b != null; b = te.next()) {
        sb.append(b.utf8ToString()).append(' ');
      }
      sb.append("] ");
    }
    return sb.toString();
  }

  /**
   * doc 0: FIELD holds only terms that get removed; doc 1: an unrelated term-vector field; doc 2:
   * FIELD keeps a term (so the field still exists after filtering), with term vectors that have no
   * offsets for this document (term-vector options may differ per document).
   */
  private Directory source(boolean offsetsOnA) throws IOException {
    Directory src = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    FieldType typeA = new FieldType(TextField.TYPE_NOT_STORED);
    typeA.setStoreTermVectors(true);
    typeA.setStoreTermVectorOffsets(offsetsOnA);
    FieldType plain = new FieldType(TextField.TYPE_NOT_STORED);
    plain.setStoreTermVectors(true);
    try (IndexWriter w = new IndexWriter(src, iwc)) {
      Document d0 = new Document();
      d0.add(new StringField("id", "0", Field.Store.NO));
      d0.add(new Field(FIELD, "alpha beta", typeA));
      w.addDocument(d0);
      Document d1 = new Document();
      d1.add(new StringField("id", "1", Field.Store.NO));
      d1.add(new Field("body", "public text here", plain));
      w.addDocument(d1);
      Document d2 = new Document();
      d2.add(new StringField("id", "2", Field.Store.NO));
      d2.add(new Field(FIELD, "gamma", plain)); // term vectors without offsets for this document
      w.addDocument(d2);
    }
    return src;
  }

  private static CodecReader filtered(DirectoryReader r) throws IOException {
    assertEquals(1, r.leaves().size());
    return SlowCodecReaderWrapper.wrap(new DropTerms(r.leaves().get(0).reader()));
  }

  /** Control: CheckIndex accepts the filtered reader itself. */
  public void testFilteredReaderPassesCheckIndex() throws Exception {
    for (boolean offsets : new boolean[] {true, false}) {
      try (Directory src = source(offsets);
          DirectoryReader r = DirectoryReader.open(src)) {
        CodecReader cr = filtered(r);
        CheckIndex.Status.TermIndexStatus postings =
            CheckIndex.testPostings(cr, null, false, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS, true);
        CheckIndex.Status.TermVectorStatus vectors =
            CheckIndex.testTermVectors(
                cr, null, false, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS, true);
        CheckIndex.Status.FieldNormStatus norms = CheckIndex.testFieldNorms(cr, null, true);
        assertNull(postings.error);
        assertNull(vectors.error);
        assertNull(norms.error);
        assertEquals("[gamma]", termsOf(cr.terms(FIELD)));
        assertEquals("[]", termsOf(cr.termVectors().get(0).terms(FIELD)));
      }
    }
  }

  private static String termsOf(Terms t) throws IOException {
    StringBuilder sb = new StringBuilder("[");
    TermsEnum te = t.iterator();
    for (BytesRef b = te.next(); b != null; b = te.next()) {
      sb.append(sb.length() > 1 ? " " : "").append(b.utf8ToString());
    }
    return sb.append("]").toString();
  }

  /** FIELD stores offsets: the other documents of the chunk must stay readable. */
  public void testOtherDocumentOfTheChunkStaysReadable() throws Exception {
    try (Directory src = source(true);
        BaseDirectoryWrapper dst = newDirectory()) {
      dst.setCheckIndexOnClose(false); // checked explicitly below
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dst, iwc);
          DirectoryReader r = DirectoryReader.open(src)) {
        w.addIndexes(filtered(r));
        w.commit();
      }
      String doc1;
      try (DirectoryReader r2 = DirectoryReader.open(dst)) {
        try {
          doc1 = dump(r2.termVectors().get(1));
        } catch (IOException e) {
          doc1 = e.toString();
        }
      }
      boolean clean;
      try (CheckIndex ci = new CheckIndex(dst)) {
        clean = ci.checkIndex().clean;
      }
      assertEquals(
          "doc 1 term vectors=body=[here public text ] , checkIndex clean=true",
          "doc 1 term vectors=" + doc1 + ", checkIndex clean=" + clean);
    }
  }

  /** FIELD has no offsets: a later re-encoding merge must not fail. */
  public void testLaterMergeOfTheSegmentSucceeds() throws Exception {
    try (Directory src = source(false);
        Directory dst = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      IndexWriter w = new IndexWriter(dst, iwc);
      try (DirectoryReader r = DirectoryReader.open(src)) {
        w.addIndexes(filtered(r));
        w.commit();
      }
      // a deletion makes the merge re-encode the term vectors document by document
      w.deleteDocuments(new Term("id", "1"));
      String merge;
      try {
        w.forceMerge(1);
        w.commit();
        merge = "ok";
      } catch (Exception e) {
        merge = e.toString();
      }
      Throwable tragic = w.getTragicException();
      w.rollback();
      assertEquals("merge=ok, tragic=null", "merge=" + merge + ", tragic=" + tragic);
    }
  }
}
