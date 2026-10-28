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
package org.apache.lucene.search.suggest.document;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent verifier repro. A SuggestField and a ContextSuggestField under one field name are
 * both accepted by addDocument. The type conflict is detected only while the completion postings
 * are written (flush or merge). Asserted contract: a rejected (invalid) input must not close the
 * IndexWriter, and an index that committed cleanly must stay mergeable.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "diagnostic output only")
public class TestCompletionMixedTypes extends LuceneTestCase {

  private static final String FIELD = "suggest";

  /** Production default codec, with FIELD routed to the matching completion postings format. */
  private static Codec completionCodec() {
    final Codec base = TestUtil.getDefaultCodec();
    final PostingsFormat basePf = TestUtil.getDefaultPostingsFormat();
    String suffix = basePf.getName().replaceAll("[^0-9]", "");
    Set<String> available = new HashSet<>(PostingsFormat.availablePostingsFormats());
    String name = "Completion" + suffix;
    assertTrue("no completion format " + name + " in " + available, available.contains(name));
    final PostingsFormat completion = PostingsFormat.forName(name);
    final PerFieldPostingsFormat perField =
        new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return FIELD.equals(field) ? completion : basePf;
          }
        };
    // Same name as the base codec, so the index is readable through SPI.
    return new FilterCodec(base.getName(), base) {
      @Override
      public PostingsFormat postingsFormat() {
        return perField;
      }
    };
  }

  private static IndexWriterConfig config(boolean merges) {
    Analyzer analyzer = new StandardAnalyzer();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setCodec(completionCodec());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(1000);
    iwc.setRAMBufferSizeMB(64);
    if (merges) {
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(10); // two segments never trigger a natural merge
      iwc.setMergePolicy(mp);
    } else {
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    }
    return iwc;
  }

  private static Document doc(String id, Field extra) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (extra != null) {
      d.add(extra);
    }
    return d;
  }

  private static boolean chainHas(Throwable t, String text) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c.getMessage() != null && c.getMessage().contains(text)) {
        return true;
      }
    }
    return false;
  }

  private static int committedDocs(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return r.numDocs();
    }
  }

  private static void assertWriterSurvived(String what, IndexWriter w, Throwable thrown) {
    System.out.println(
        "V1 "
            + what
            + ": thrown="
            + thrown
            + " isOpen="
            + w.isOpen()
            + " tragic="
            + w.getTragicException());
    if (thrown != null) {
      for (StackTraceElement e : thrown.getStackTrace()) {
        String s = e.toString();
        if (s.contains("CompletionFieldsConsumer")
            || s.contains("IndexWriter.merge(")
            || s.contains("SegmentMerger.mergeTerms")
            || s.contains("DocumentsWriterPerThread.flush")) {
          System.out.println("V1   origin frame: " + s);
        }
      }
    }
    assertNull(
        what + " closed the IndexWriter with tragic exception: " + w.getTragicException(),
        w.getTragicException());
    assertTrue(what + " closed the IndexWriter", w.isOpen());
  }

  /** Two accepted documents (one per type) in one in-memory segment. */
  public void testMixInOneBufferedSegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(false));
      w.addDocument(doc("committed", null));
      w.commit();
      // all three are accepted by addDocument
      w.addDocument(doc("unrelated", null));
      w.addDocument(doc("plain-suggest", new SuggestField(FIELD, "apple", 1)));
      w.addDocument(doc("context-suggest", new ContextSuggestField(FIELD, "apricot", 2, "ctx")));
      Throwable t = expectThrows(Throwable.class, w::commit);
      assertTrue("unexpected exception: " + t, chainHas(t, "mixed types"));
      try {
        assertWriterSurvived("commit of a buffered SuggestField+ContextSuggestField mix", w, t);
      } finally {
        System.out.println("V1 committed docs after failure: " + committedDocs(dir));
        w.close();
      }
    }
  }

  /** Two segments, one type each, that flushed and committed cleanly; then forceMerge. */
  public void testMixAcrossCommittedSegmentsMerge() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(true));
      w.addDocument(doc("plain-suggest", new SuggestField(FIELD, "apple", 1)));
      w.commit();
      w.addDocument(doc("context-suggest", new ContextSuggestField(FIELD, "apricot", 2, "ctx")));
      w.commit(); // both commits succeed
      TestUtil.checkIndex(dir); // the committed index is valid
      w.addDocument(doc("unrelated-uncommitted", null));
      Throwable t = expectThrows(Throwable.class, () -> w.forceMerge(1));
      assertTrue("unexpected exception: " + t, chainHas(t, "mixed types"));
      try {
        assertWriterSurvived("forceMerge(1) of two cleanly committed segments", w, t);
      } finally {
        System.out.println("V1 committed docs after merge failure: " + committedDocs(dir));
        w.close();
      }
    }
  }

  /** The committed two-segment state persists: a brand-new writer fails the same way. */
  public void testNewWriterCannotMergeCommittedIndex() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(true))) {
        w.addDocument(doc("plain-suggest", new SuggestField(FIELD, "apple", 1)));
        w.commit();
        w.addDocument(doc("context-suggest", new ContextSuggestField(FIELD, "apricot", 2, "ctx")));
      } // clean close: commit on close succeeds
      TestUtil.checkIndex(dir);
      assertEquals(2, committedDocs(dir));

      // query side reports the mix as a clean IllegalStateException (by design)
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SuggestIndexSearcher s = new SuggestIndexSearcher(r);
        Throwable q =
            expectThrows(
                Throwable.class,
                () ->
                    s.suggest(
                        new PrefixCompletionQuery(new StandardAnalyzer(), new Term(FIELD, "ap")),
                        3,
                        false));
        System.out.println("V1 query on two-segment index: " + q);
      }

      IndexWriter w2 = new IndexWriter(dir, config(true));
      Throwable t = expectThrows(Throwable.class, () -> w2.forceMerge(1));
      assertTrue("unexpected exception: " + t, chainHas(t, "mixed types"));
      try {
        assertWriterSurvived("forceMerge(1) in a new writer on the committed index", w2, t);
      } finally {
        w2.close();
        // diagnostic: only deleting every document of one type makes the index mergeable again
        try (IndexWriter w3 = new IndexWriter(dir, config(true))) {
          w3.deleteDocuments(new Term("id", "context-suggest"));
          w3.forceMerge(1);
          System.out.println("V1 merge after deleting the ContextSuggestField doc: ok");
        } catch (Throwable e) {
          System.out.println("V1 merge after deleting the ContextSuggestField doc: " + e);
        }
      }
    }
  }

  /** No explicit merge: a natural merge on a ConcurrentMergeScheduler thread after two commits. */
  public void testNaturalMergeOnMergeThread() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      final Throwable[] mergeFailure = new Throwable[1];
      ConcurrentMergeScheduler cms =
          new ConcurrentMergeScheduler() {
            @Override
            protected void handleMergeException(Throwable exc) {
              mergeFailure[0] = exc; // keep the merge thread's exception out of the test runner
            }
          };
      IndexWriterConfig iwc = config(true);
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(2); // two segments trigger a natural merge
      mp.setMinMergeDocs(1);
      iwc.setMergePolicy(mp);
      iwc.setMergeScheduler(cms);
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc("plain-suggest", new SuggestField(FIELD, "apple", 1)));
      w.commit();
      w.addDocument(doc("context-suggest", new ContextSuggestField(FIELD, "apricot", 2, "ctx")));
      try {
        w.commit(); // flushes the second segment and schedules a natural merge of the two
      } catch (Throwable t) {
        System.out.println("V1 second commit raced with the merge failure: " + t);
      }
      cms.sync();
      System.out.println("V1 natural merge thread exception: " + mergeFailure[0]);
      try {
        assertWriterSurvived("a natural background merge of two committed segments", w, null);
      } finally {
        w.close();
      }
    }
  }

  /** Upstream's own case (one document holding both types), plus a check of the writer state. */
  public void testOneDocumentWithBothTypes() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(false));
      w.addDocument(doc("unrelated", null));
      Document d = doc("both", new SuggestField(FIELD, "apple", 1));
      d.add(new ContextSuggestField(FIELD, "apricot", 2, "ctx"));
      w.addDocument(d); // accepted
      Throwable t = expectThrows(Throwable.class, w::commit);
      assertTrue("unexpected exception: " + t, chainHas(t, "mixed types"));
      try {
        assertWriterSurvived("commit of one document holding both types", w, t);
      } finally {
        w.close();
      }
    }
  }

  /** Control: the same shapes with a single type must work (vacuity check of the harness). */
  public void testControlSingleTypeMerges() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(true))) {
        w.addDocument(doc("a", new ContextSuggestField(FIELD, "apple", 1, "ctx")));
        w.commit();
        w.addDocument(doc("b", new ContextSuggestField(FIELD, "apricot", 2, "ctx")));
        w.addDocument(doc("c", new ContextSuggestField(FIELD, "avocado", 3, "ctx2")));
        w.commit();
        w.forceMerge(1);
        assertNull(w.getTragicException());
        assertTrue(w.isOpen());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        SuggestIndexSearcher s = new SuggestIndexSearcher(r);
        TopSuggestDocs hits =
            s.suggest(
                new ContextQuery(
                    new PrefixCompletionQuery(new StandardAnalyzer(), new Term(FIELD, "a"))),
                10,
                false);
        assertEquals(3, hits.scoreDocs.length);
      }
    }
  }
}
