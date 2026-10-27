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
package org.apache.lucene.codecs.perfield;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterCodecReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probe of the reader-wrapper route: a per-field postings codec in every writer session, and a
 * merge source (addIndexes(CodecReader) or OneMerge.wrapForMerge) whose postings view omits an
 * indexed field while its FieldInfos keep that field with its per-field attributes.
 */
public class TestPerFieldPostingsWrapperRoute extends LuceneTestCase {

  /** The field whose postings the wrapper hides; "-Dprobe.hidden=none" gives the control. */
  static final String HIDDEN = System.getProperty("probe.hidden", "tag");

  private static Codec tagUsesFst50() {
    return new Lucene104Codec() {
      final PostingsFormat fst50 = PostingsFormat.forName("FST50");

      @Override
      public PostingsFormat getPostingsFormatForField(String field) {
        return field.equals("tag") ? fst50 : super.getPostingsFormatForField(field);
      }
    };
  }

  private static Document doc(String id, String tag) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (tag != null) {
      doc.add(new StringField("tag", tag, Field.Store.NO));
    }
    return doc;
  }

  /** Postings view without {@code hidden}; FieldInfos unchanged. */
  static FieldsProducer hide(FieldsProducer in, String hidden) {
    List<String> names = new ArrayList<>();
    for (String f : in) {
      if (f.equals(hidden) == false) {
        names.add(f);
      }
    }
    return new FieldsProducer() {
      @Override
      public Iterator<String> iterator() {
        return names.iterator();
      }

      @Override
      public Terms terms(String field) {
        return field.equals(hidden) ? null : in.terms(field);
      }

      @Override
      public int size() {
        return names.size();
      }

      @Override
      public void close() {}

      @Override
      public void checkIntegrity(MergePolicy.OneMerge merge) throws IOException {
        in.checkIntegrity(merge);
      }

      @Override
      public FieldsProducer getMergeInstance() {
        return hide(in.getMergeInstance(), hidden);
      }
    };
  }

  static CodecReader hidePostings(CodecReader reader, String hidden) {
    return new FilterCodecReader(reader) {
      @Override
      public FieldsProducer getPostingsReader() {
        return hide(in.getPostingsReader(), hidden);
      }

      @Override
      public CacheHelper getCoreCacheHelper() {
        return null;
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null;
      }
    };
  }

  /** Wraps every forced merge so that its sources hide the postings of "tag". */
  static final class HidingMergePolicy extends FilterMergePolicy {
    HidingMergePolicy(MergePolicy in) {
      super(in);
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos infos,
        int maxSegmentCount,
        Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext ctx)
        throws IOException {
      MergeSpecification spec = super.findForcedMerges(infos, maxSegmentCount, segmentsToMerge, ctx);
      if (spec == null) {
        return null;
      }
      MergeSpecification out = new MergeSpecification();
      for (OneMerge m : spec.merges) {
        out.add(
            new OneMerge(m.segments) {
              @Override
              public CodecReader wrapForMerge(CodecReader reader) {
                return hidePostings(reader, HIDDEN);
              }
            });
      }
      return out;
    }
  }

  /** addIndexes(CodecReader) with a source whose postings view hides "tag". */
  public void testAddIndexesWrapperHidesPostings() throws Exception {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig().setCodec(tagUsesFst50()))) {
        w.addDocument(doc("0", "t"));
        w.addDocument(doc("1", null));
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w = new IndexWriter(dst, new IndexWriterConfig().setCodec(tagUsesFst50()))) {
        List<CodecReader> wrapped = new ArrayList<>();
        for (LeafReaderContext ctx : r.leaves()) {
          wrapped.add(hidePostings((CodecReader) ctx.reader(), HIDDEN));
        }
        w.addIndexes(wrapped.toArray(new CodecReader[0]));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        assertEquals(2, r.numDocs());
        assertEquals(HIDDEN.equals("tag") ? 0 : 1, r.docFreq(new Term("tag", "t")));
      }
    }
  }

  /** A merge policy whose OneMerge.wrapForMerge hides "tag" postings; per-field codec throughout. */
  public void testWrapForMergeHidesPostings() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(tagUsesFst50())
              .setMergePolicy(new HidingMergePolicy(new TieredMergePolicy()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("0", "t"));
        w.addDocument(doc("1", null));
        w.commit();
        w.addDocument(doc("2", null));
        w.commit();
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(3, r.numDocs());
        assertEquals(HIDDEN.equals("tag") ? 0 : 1, r.docFreq(new Term("tag", "t")));
      }
    }
  }
}
