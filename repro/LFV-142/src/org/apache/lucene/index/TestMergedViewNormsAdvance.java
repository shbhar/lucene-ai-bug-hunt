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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Public-API path: the merged CodecReader that IndexWriter hands to {@link
 * MergePolicy.OneMerge#reorder} returns norms whose advance(target) can return a document before
 * target. Uses only public API (MergePolicy/OneMerge/FilterMergePolicy, LeafReader#getNormValues,
 * DocIdSetIterator#advance).
 */
public class TestMergedViewNormsAdvance extends LuceneTestCase {

  /** Records, inside reorder(), every advance(t) result of the merged view's norms. */
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
              public org.apache.lucene.index.Sorter.DocMap reorder(
                  CodecReader reader, Directory dir, Executor executor) throws IOException {
                probe(reader);
                return null; // keep the doc order; we only observe the merged view
              }
            });
      }
      return wrapped;
    }

    void probe(CodecReader reader) throws IOException {
      probedReaders++;
      // ground truth from nextDoc() on a fresh iterator
      List<Integer> docsWithNorms = new ArrayList<>();
      NumericDocValues it = reader.getNormValues("body");
      for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
        docsWithNorms.add(d);
      }
      for (int target = 0; target < reader.maxDoc(); target++) {
        int expected = DocIdSetIterator.NO_MORE_DOCS;
        for (int d : docsWithNorms) {
          if (d >= target) {
            expected = d;
            break;
          }
        }
        int actual = reader.getNormValues("body").advance(target); // fresh iterator each time
        if (actual != expected) {
          violations.add("advance(" + target + ") returned " + actual + ", expected " + expected);
        }
      }
    }
  }

  public void testReorderSeesConsistentNorms() throws IOException {
    ProbeMergePolicy mp = new ProbeMergePolicy(newLogMergePolicy(10));
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergeScheduler(new SerialMergeScheduler())
                    .setMergePolicy(mp))) {
      Document withBody = new Document();
      withBody.add(new TextField("body", "some text", Field.Store.NO));
      w.addDocument(withBody); // segment: 1 doc with norms
      w.commit();
      w.addDocument(new Document()); // segment: 2 docs without the field
      w.addDocument(new Document());
      w.commit();
      w.addDocument(withBody); // segment: 1 doc with norms
      w.commit();
      w.forceMerge(1);
    }
    assertEquals("reorder() must have been called on a merged view", 1, mp.probedReaders);
    assertTrue(
        "norms of the merged view violate DocIdSetIterator#advance: " + mp.violations,
        mp.violations.isEmpty());
  }
}
