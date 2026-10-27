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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.IOUtils;

/**
 * addIndexes(CodecReader) from a reader that removes one term of field "a" consistently from the
 * postings and the term vectors. Doc 0's "a" held only that term, so the reader lists "a" in doc
 * 0's term vectors with a Terms that has no terms. The segment that addIndexes writes must read
 * back and merge like any other.
 */
public class TestAddIndexesZeroTermVectorField extends LuceneTestCase {

  private static FieldType tvType(boolean offsets) {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setOmitNorms(true);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.setStoreTermVectorOffsets(offsets);
    ft.freeze();
    return ft;
  }

  private static IndexWriterConfig iwc() {
    IndexWriterConfig c = new IndexWriterConfig(new StandardAnalyzer());
    c.setCodec(TestUtil.getDefaultCodec());
    c.setMergeScheduler(new SerialMergeScheduler());
    return c;
  }

  private static Document doc(String id, String field, String text, FieldType ft) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    if (field != null) {
      d.add(new Field(field, text, ft));
    }
    return d;
  }

  /** Writes docs into src (one segment), then addIndexes(src without "secret" in "a"). */
  private static void addFiltered(Directory src, IndexWriter dst, Document... docs)
      throws IOException {
    try (IndexWriter w = new IndexWriter(src, iwc().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (Document d : docs) {
        w.addDocument(d);
      }
    }
    try (DirectoryReader r = DirectoryReader.open(src)) {
      assertEquals(1, r.leaves().size());
      LeafReader filtered = new DropTermReader(getOnlyLeafReader(r), "a", new BytesRef("secret"));
      dst.addIndexes(SlowCodecReaderWrapper.wrap(filtered));
      dst.commit();
    }
  }

  private static String tvTerms(IndexReader r, int doc, String field) {
    try {
      Fields fields = r.termVectors().get(doc);
      if (fields == null) {
        return "no vectors";
      }
      Terms terms = fields.terms(field);
      if (terms == null) {
        return "null";
      }
      List<String> out = new ArrayList<>();
      TermsEnum te = terms.iterator();
      for (BytesRef t = te.next(); t != null; t = te.next()) {
        out.add(t.utf8ToString());
      }
      return out.toString();
    } catch (Throwable t) {
      return t.toString();
    }
  }

  /** CheckIndex with slow checks; "true" if clean, else the first line of the error. */
  private static String checkIndexClean(Directory dir) {
    try {
      return Boolean.toString(TestUtil.checkIndex(dir).clean);
    } catch (Throwable t) {
      String m = t.toString();
      int nl = m.indexOf('\n');
      return nl < 0 ? m : m.substring(0, nl);
    }
  }

  /** "a" stores offsets; nothing else in the chunk has offsets. Doc 1 must stay readable. */
  public void testOtherDocumentOfChunkReadable() throws Exception {
    try (Directory src = newDirectory();
        BaseDirectoryWrapper dst = newDirectory()) {
      dst.setCheckIndexOnClose(false);
      try (IndexWriter w = new IndexWriter(dst, iwc())) {
        addFiltered(
            src,
            w,
            doc("0", "a", "secret", tvType(true)),
            doc("1", "body", "here public text", tvType(false)));
      }
      String observed;
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        observed =
            "maxDoc="
                + r.maxDoc()
                + " doc1.body="
                + tvTerms(r, 1, "body")
                + " checkIndexClean="
                + checkIndexClean(dst);
      }
      assertEquals("maxDoc=2 doc1.body=[here, public, text] checkIndexClean=true", observed);
    }
  }

  /** "a" without offsets. A merge that re-encodes the term vectors must succeed. */
  public void testReencodingMergeSucceeds() throws Exception {
    runReencodingMerge(
        doc("0", "a", "secret", tvType(false)),
        doc("1", "body", "here public text", tvType(false)),
        doc("2", "body", "to be deleted", tvType(false)));
  }

  /** Same, but no other surviving document of the chunk has a term-vector field. */
  public void testReencodingMergeSucceedsSingleField() throws Exception {
    runReencodingMerge(
        doc("0", "a", "secret", tvType(false)),
        doc("1", null, null, null),
        doc("2", "body", "to be deleted", tvType(false)));
  }

  private void runReencodingMerge(Document... docs) throws Exception {
    try (Directory src = newDirectory();
        BaseDirectoryWrapper dst = newDirectory()) {
      dst.setCheckIndexOnClose(false);
      IndexWriter w = new IndexWriter(dst, iwc());
      String observed;
      try {
        addFiltered(src, w, docs);
        String step = "deleteDocuments";
        Throwable mergeFailure = null;
        try {
          // the segment has a deletion now, so its term vectors are re-encoded, not bulk-copied
          w.deleteDocuments(new Term("id", "2"));
          step = "commit";
          w.commit(); // with the default merge policy this already runs the merge
          step = "forceMerge";
          w.forceMerge(1);
        } catch (Throwable t) {
          mergeFailure = t;
        }
        observed =
            "merge=" + (mergeFailure == null ? "ok" : "failed in " + step + ": " + mergeFailure);
        observed += ", tragic=" + w.getTragicException();
        if (mergeFailure == null) {
          w.commit();
          try (DirectoryReader r = DirectoryReader.open(dst)) {
            observed += ", segments=" + r.leaves().size() + ", deleted=" + r.numDeletedDocs();
          }
        }
      } finally {
        IOUtils.closeWhileHandlingException(w);
      }
      assertEquals("merge=ok, tragic=null, segments=1, deleted=0", observed);
    }
  }

  /**
   * Hides one term of one field, consistently in the postings and in the term vectors. Its Terms
   * report size() == -1 (allowed by the Terms javadoc) and recompute the other statistics from the
   * remaining terms.
   */
  static final class DropTermReader extends FilterLeafReader {
    private final String field;
    private final BytesRef dropped;

    DropTermReader(LeafReader in, String field, BytesRef dropped) {
      super(in);
      this.field = field;
      this.dropped = dropped;
    }

    @Override
    public Terms terms(String f) {
      try {
        Terms t = in.terms(f);
        return t == null || field.equals(f) == false ? t : new DropTerms(t, dropped, maxDoc());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public TermVectors termVectors() throws IOException {
      final TermVectors orig = super.termVectors();
      return new TermVectors() {
        @Override
        public Fields get(int doc) throws IOException {
          Fields fields = orig.get(doc);
          if (fields == null) {
            return null;
          }
          return new FilterFields(fields) {
            @Override
            public Terms terms(String f) {
              try {
                Terms t = in.terms(f);
                return t == null || field.equals(f) == false ? t : new DropTerms(t, dropped, 1);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
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

  /** Terms without the dropped term; statistics recomputed from the remaining terms. */
  static final class DropTerms extends Terms {
    private final Terms in;
    private final BytesRef dropped;
    private final long sumTotalTermFreq;
    private final long sumDocFreq;
    private final int docCount;

    DropTerms(Terms in, BytesRef dropped, int maxDoc) throws IOException {
      this.in = in;
      this.dropped = dropped;
      long ttf = 0;
      long df = 0;
      FixedBitSet docs = new FixedBitSet(Math.max(1, maxDoc));
      TermsEnum te = iterator();
      PostingsEnum pe = null;
      while (te.next() != null) {
        ttf += te.totalTermFreq();
        df += te.docFreq();
        pe = te.postings(pe, PostingsEnum.NONE);
        for (int d = pe.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = pe.nextDoc()) {
          docs.set(d);
        }
      }
      this.sumTotalTermFreq = ttf;
      this.sumDocFreq = df;
      this.docCount = docs.cardinality();
    }

    @Override
    public TermsEnum iterator() throws IOException {
      return new DropTermsEnum(in.iterator(), dropped);
    }

    @Override
    public long size() {
      return -1; // "-1 if this measure isn't stored"
    }

    @Override
    public long getSumTotalTermFreq() {
      return sumTotalTermFreq;
    }

    @Override
    public long getSumDocFreq() {
      return sumDocFreq;
    }

    @Override
    public int getDocCount() {
      return docCount;
    }

    @Override
    public boolean hasFreqs() {
      return in.hasFreqs();
    }

    @Override
    public boolean hasOffsets() {
      return in.hasOffsets();
    }

    @Override
    public boolean hasPositions() {
      return in.hasPositions();
    }

    @Override
    public boolean hasPayloads() {
      return in.hasPayloads();
    }
  }

  /** TermsEnum that skips the dropped term; supports next() and seekCeil(), not ords. */
  static final class DropTermsEnum extends BaseTermsEnum {
    private final TermsEnum in;
    private final BytesRef dropped;

    DropTermsEnum(TermsEnum in, BytesRef dropped) {
      this.in = in;
      this.dropped = dropped;
    }

    @Override
    public BytesRef next() throws IOException {
      BytesRef t;
      do {
        t = in.next();
      } while (t != null && t.equals(dropped));
      return t;
    }

    @Override
    public SeekStatus seekCeil(BytesRef text) throws IOException {
      SeekStatus s = in.seekCeil(text);
      if (s != SeekStatus.END && in.term().equals(dropped)) {
        return next() == null ? SeekStatus.END : SeekStatus.NOT_FOUND;
      }
      return s;
    }

    @Override
    public void seekExact(long ord) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long ord() {
      throw new UnsupportedOperationException();
    }

    @Override
    public BytesRef term() throws IOException {
      return in.term();
    }

    @Override
    public int docFreq() throws IOException {
      return in.docFreq();
    }

    @Override
    public long totalTermFreq() throws IOException {
      return in.totalTermFreq();
    }

    @Override
    public PostingsEnum postings(PostingsEnum reuse, int flags) throws IOException {
      return in.postings(reuse, flags);
    }

    @Override
    public ImpactsEnum impacts(int flags) throws IOException {
      return in.impacts(flags);
    }
  }
}
