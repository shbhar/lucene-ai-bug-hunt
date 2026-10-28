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
package org.apache.lucene.misc.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Security-assessment probe: what state is the IndexWriter in after a merge through
 * BPReorderingMergePolicy + BpVectorReorderer fails because one merged segment has no vectors
 * for the field? Each test prints a PROBE line with its observations and then asserts the
 * behaviour a correct implementation would show (so the stock build fails and a fixed build
 * passes).
 */
public class TestFieldlessSegmentMergeWriterState extends LuceneTestCase {

  /** Records merge-thread exceptions instead of rethrowing them as uncaught exceptions. */
  static final class RecordingCMS extends ConcurrentMergeScheduler {
    final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    @Override
    protected void handleMergeException(Throwable exc) {
      errors.add(exc);
    }
  }

  private static IndexWriterConfig config(MergePolicy in, RecordingCMS cms) {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(new BPReorderingMergePolicy(in, new BpVectorReorderer("vector")));
    iwc.setMergeScheduler(cms);
    iwc.setMaxFullFlushMergeWaitMillis(0);
    return iwc;
  }

  private static void addDocs(IndexWriter w, String prefix, int count, boolean withVector)
      throws IOException {
    for (int i = 0; i < count; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", prefix + i, Field.Store.YES));
      if (withVector) {
        doc.add(new KnnFloatVectorField("vector", new float[] {i % 7, 1 + i % 3}));
      }
      w.addDocument(doc);
    }
  }

  private static String rootCause(Throwable t) {
    if (t == null) {
      return "none";
    }
    Throwable c = t;
    while (c.getCause() != null && c.getCause() != c) {
      c = c.getCause();
    }
    return c.getClass().getSimpleName();
  }

  private static void closeQuietly(IndexWriter w) {
    try {
      w.close();
    } catch (Throwable _) {
      // writer may already be closed by a tragic event
    }
  }

  /**
   * forceMerge(1) over a segment with vectors and a segment without: observe the writer, the
   * changes made after the last commit, and the index that is left on disk.
   */
  public void testForceMergeWriterState() throws Exception {
    RecordingCMS cms = new RecordingCMS();
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(new LogDocMergePolicy(), cms));
      Throwable forceMergeFailure = null;
      boolean openAfter;
      Throwable tragic;
      int nrtNumDocs;
      int nrtV0;
      try {
        addDocs(w, "v", 100, true);
        w.commit();
        addDocs(w, "n", 10, false);
        w.commit();
        // changes after the last commit: 5 new documents and one delete
        addDocs(w, "u", 5, true);
        w.deleteDocuments(new Term("id", "v0"));
        try (DirectoryReader nrt = DirectoryReader.open(w)) {
          nrtNumDocs = nrt.numDocs();
          nrtV0 = new IndexSearcher(nrt).count(new TermQuery(new Term("id", "v0")));
        }
        try {
          w.forceMerge(1);
        } catch (Throwable t) {
          // IOException "background merge hit exception" or, if the tragic event closed the
          // writer first, IllegalStateException "this writer hit an unrecoverable error"
          forceMergeFailure = t;
        }
        cms.sync();
        openAfter = w.isOpen();
        tragic = w.getTragicException();
      } finally {
        closeQuietly(w);
      }
      int diskNumDocs;
      int diskLeaves;
      int diskV0;
      int diskU;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        diskNumDocs = r.numDocs();
        diskLeaves = r.leaves().size();
        IndexSearcher s = new IndexSearcher(r);
        diskV0 = s.count(new TermQuery(new Term("id", "v0")));
        diskU = s.count(new TermQuery(new Term("id", "u0")));
      }
      TestUtil.checkIndex(dir);
      System.out.println(
          "PROBE forceMerge: forceMergeFailure="
              + (forceMergeFailure == null ? "none" : forceMergeFailure.getClass().getSimpleName())
              + " rootCause="
              + rootCause(forceMergeFailure)
              + " mergeThreadErrors="
              + cms.errors.size()
              + " writerOpenAfter="
              + openAfter
              + " tragicException="
              + rootCause(tragic)
              + " nrtNumDocsBefore="
              + nrtNumDocs
              + " nrtHitsDeletedV0Before="
              + nrtV0
              + " diskNumDocsAfter="
              + diskNumDocs
              + " diskLeavesAfter="
              + diskLeaves
              + " diskHitsDeletedV0After="
              + diskV0
              + " diskHitsUncommittedU0After="
              + diskU
              + " checkIndex=clean");
      assertNull("forceMerge failed: " + forceMergeFailure, forceMergeFailure);
      assertTrue("writer closed by tragic event " + tragic, openAfter);
    }
  }

  /**
   * Natural merges (LogDocMergePolicy, mergeFactor 2): the failure repeats each time a writer
   * is reopened on the same index and adds one document.
   */
  public void testNaturalMergeRepeatsAcrossReopen() throws Exception {
    try (Directory dir = newDirectory()) {
      // build the two segments without merging
      IndexWriterConfig base = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      base.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w0 = new IndexWriter(dir, base)) {
        // equal doc counts, so LogDocMergePolicy puts both segments on the same level
        addDocs(w0, "v", 100, true);
        w0.commit();
        addDocs(w0, "n", 100, false);
        w0.commit();
      }
      int cycles = 3;
      int closedCycles = 0;
      StringBuilder sb = new StringBuilder();
      for (int cycle = 0; cycle < cycles; cycle++) {
        LogDocMergePolicy ldmp = new LogDocMergePolicy();
        ldmp.setMergeFactor(2);
        RecordingCMS cms = new RecordingCMS();
        IndexWriter w = new IndexWriter(dir, config(ldmp, cms));
        Throwable commitFailure = null;
        boolean open;
        Throwable tragic;
        try {
          addDocs(w, "c" + cycle + "_", 1, true);
          try {
            w.commit();
          } catch (Throwable t) {
            commitFailure = t;
          }
          cms.sync();
          open = w.isOpen();
          tragic = w.getTragicException();
        } finally {
          closeQuietly(w);
        }
        if (open == false) {
          closedCycles++;
        }
        int leaves;
        int numDocs;
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          leaves = r.leaves().size();
          numDocs = r.numDocs();
        }
        sb.append(" cycle")
            .append(cycle)
            .append("{commitFailure=")
            .append(rootCause(commitFailure))
            .append(" mergeThreadErrors=")
            .append(cms.errors.size())
            .append(" firstError=")
            .append(cms.errors.isEmpty() ? "none" : rootCause(cms.errors.get(0)))
            .append(" writerOpenAfter=")
            .append(open)
            .append(" tragic=")
            .append(rootCause(tragic))
            .append(" diskLeaves=")
            .append(leaves)
            .append(" diskNumDocs=")
            .append(numDocs)
            .append("}");
      }
      TestUtil.checkIndex(dir);
      System.out.println(
          "PROBE naturalMerge: closedCycles=" + closedCycles + "/" + cycles + sb + " checkIndex=clean");
      assertEquals("writer closed in " + closedCycles + " of " + cycles + " cycles", 0, closedCycles);
    }
  }

  /**
   * Default TieredMergePolicy, natural merges only: twelve small flushed segments, one of which
   * has no document with the vector field.
   */
  public void testDefaultTieredNaturalMerge() throws Exception {
    try (Directory dir = newDirectory()) {
      RecordingCMS cms = new RecordingCMS();
      IndexWriter w = new IndexWriter(dir, config(new TieredMergePolicy(), cms));
      Throwable failure = null;
      boolean open;
      Throwable tragic;
      int committedSegments = 0;
      try {
        for (int seg = 0; seg < 12 && failure == null && w.isOpen(); seg++) {
          addDocs(w, "s" + seg + "_", 20, seg != 5);
          try {
            w.commit();
            committedSegments++;
          } catch (Throwable t) {
            failure = t;
          }
          cms.sync();
        }
        open = w.isOpen();
        tragic = w.getTragicException();
      } finally {
        closeQuietly(w);
      }
      int leaves;
      int numDocs;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        leaves = r.leaves().size();
        numDocs = r.numDocs();
      }
      TestUtil.checkIndex(dir);
      System.out.println(
          "PROBE tieredNaturalMerge: committedSegments="
              + committedSegments
              + " commitFailure="
              + rootCause(failure)
              + " mergeThreadErrors="
              + cms.errors.size()
              + " firstError="
              + (cms.errors.isEmpty() ? "none" : rootCause(cms.errors.get(0)))
              + " writerOpenAfter="
              + open
              + " tragic="
              + rootCause(tragic)
              + " diskLeaves="
              + leaves
              + " diskNumDocs="
              + numDocs
              + " checkIndex=clean");
      assertTrue("writer closed by tragic event " + tragic, open);
    }
  }

  /** addIndexes(CodecReader...) with the same inputs: does the writer stay open? */
  public void testAddIndexesWriterState() throws Exception {
    try (Directory src = newDirectory();
        Directory dst = newDirectory()) {
      IndexWriterConfig base = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      base.setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w0 = new IndexWriter(src, base)) {
        addDocs(w0, "v", 100, true);
        w0.commit();
        addDocs(w0, "n", 10, false);
        w0.commit();
      }
      RecordingCMS cms = new RecordingCMS();
      Throwable addIndexesFailure = null;
      boolean open;
      Throwable tragic;
      boolean writableAfter = false;
      int dstNumDocs;
      try (DirectoryReader r = DirectoryReader.open(src)) {
        CodecReader[] readers = new CodecReader[r.leaves().size()];
        int i = 0;
        for (LeafReaderContext ctx : r.leaves()) {
          readers[i++] = (CodecReader) ctx.reader();
        }
        IndexWriter w = new IndexWriter(dst, config(new LogDocMergePolicy(), cms));
        try {
          try {
            w.addIndexes(readers);
          } catch (Throwable t) {
            addIndexesFailure = t;
          }
          cms.sync();
          open = w.isOpen();
          tragic = w.getTragicException();
          if (open) {
            addDocs(w, "after", 1, true);
            w.commit();
            writableAfter = true;
          }
        } finally {
          closeQuietly(w);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        dstNumDocs = r.numDocs();
      }
      TestUtil.checkIndex(dst);
      System.out.println(
          "PROBE addIndexes: addIndexesFailure="
              + rootCause(addIndexesFailure)
              + " writerOpenAfter="
              + open
              + " tragic="
              + rootCause(tragic)
              + " writableAfter="
              + writableAfter
              + " dstNumDocs="
              + dstNumDocs
              + " checkIndex=clean");
      assertNull("addIndexes failed: " + addIndexesFailure, addIndexesFailure);
    }
  }
}
