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
import java.util.Map;
import java.util.concurrent.Executor;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Public-API path: on the merged CodecReader that IndexWriter hands to {@link
 * MergePolicy.OneMerge#reorder}, numeric and binary doc values throw NullPointerException from
 * advance(target) after advanceExact landed on a document of a segment without the field. Uses
 * only public API (MergePolicy/OneMerge/FilterMergePolicy, LeafReader#getNumericDocValues and
 * #getBinaryDocValues, DocValuesIterator#advanceExact, DocIdSetIterator#advance).
 */
public class TestMergedViewAdvanceAfterAdvanceExact extends LuceneTestCase {

  /** The three iterator calls the probe needs (NumericDocValues and BinaryDocValues both have them). */
  interface It {
    int nextDoc() throws IOException;

    boolean advanceExact(int target) throws IOException;

    int advance(int target) throws IOException;
  }

  interface Opener {
    It open(CodecReader r) throws IOException;
  }

  static It wrap(NumericDocValues v) {
    return new It() {
      public int nextDoc() throws IOException {
        return v.nextDoc();
      }

      public boolean advanceExact(int t) throws IOException {
        return v.advanceExact(t);
      }

      public int advance(int t) throws IOException {
        return v.advance(t);
      }
    };
  }

  static It wrap(BinaryDocValues v) {
    return new It() {
      public int nextDoc() throws IOException {
        return v.nextDoc();
      }

      public boolean advanceExact(int t) throws IOException {
        return v.advanceExact(t);
      }

      public int advance(int t) throws IOException {
        return v.advance(t);
      }
    };
  }

  static final class ProbeMergePolicy extends FilterMergePolicy {
    final List<String> violations = new ArrayList<>();
    int probedReaders = 0;

    ProbeMergePolicy(MergePolicy in) {
      super(in);
    }

    @Override
    public MergeSpecification findForcedMerges(
        SegmentInfos segmentInfos,
        int maxSegmentCount,
        Map<SegmentCommitInfo, Boolean> segmentsToMerge,
        MergeContext mergeContext)
        throws IOException {
      MergeSpecification spec =
          super.findForcedMerges(segmentInfos, maxSegmentCount, segmentsToMerge, mergeContext);
      if (spec == null) {
        return null;
      }
      MergeSpecification wrapped = new MergeSpecification();
      for (OneMerge merge : spec.merges) {
        wrapped.add(
            new OneMerge(merge.segments) {
              @Override
              public Sorter.DocMap reorder(CodecReader reader, Directory dir, Executor executor)
                  throws IOException {
                probedReaders++;
                probe("numeric", reader, r -> wrap(r.getNumericDocValues("num")));
                probe("binary", reader, r -> wrap(r.getBinaryDocValues("bin")));
                return null; // keep the doc order; we only observe the merged view
              }
            });
      }
      return wrapped;
    }

    /** For every e < t < maxDoc: fresh iterator, advanceExact(e), then advance(t). */
    void probe(String what, CodecReader reader, Opener opener) throws IOException {
      List<Integer> docsWithValue = new ArrayList<>();
      It truth = opener.open(reader);
      for (int d = truth.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = truth.nextDoc()) {
        docsWithValue.add(d);
      }
      for (int e = 0; e < reader.maxDoc(); e++) {
        for (int t = e + 1; t < reader.maxDoc(); t++) {
          int expected = DocIdSetIterator.NO_MORE_DOCS;
          for (int d : docsWithValue) {
            if (d >= t) {
              expected = d;
              break;
            }
          }
          It it = opener.open(reader);
          it.advanceExact(e);
          try {
            int actual = it.advance(t);
            if (actual != expected) {
              violations.add(what + ": advanceExact(" + e + "), advance(" + t + ") returned " + actual + ", expected " + expected);
            }
          } catch (RuntimeException ex) {
            violations.add(what + ": advanceExact(" + e + "), advance(" + t + ") threw " + ex);
          }
        }
      }
    }
  }

  public void testReorderSeesUsableDocValues() throws IOException {
    ProbeMergePolicy mp = new ProbeMergePolicy(newLogMergePolicy(10));
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergeScheduler(new SerialMergeScheduler())
                    .setMergePolicy(mp))) {
      w.addDocument(new Document()); // segment: 2 docs without the fields
      w.addDocument(new Document());
      w.commit();
      Document doc = new Document(); // segment: 1 doc with both fields
      doc.add(new NumericDocValuesField("num", 42));
      doc.add(new BinaryDocValuesField("bin", new BytesRef("v")));
      w.addDocument(doc);
      w.commit();
      w.forceMerge(1);
    }
    assertEquals("reorder() must have been called on a merged view", 1, mp.probedReaders);
    assertTrue(
        "doc values of the merged view violate DocIdSetIterator#advance: " + mp.violations,
        mp.violations.isEmpty());
  }
}
