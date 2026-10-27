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
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check: copying a ParallelLeafReader (built over two separately created indexes) into
 * a new index must keep every stored field under its own name, just like the ParallelLeafReader
 * itself returns it.
 *
 * <p>Uses public API only (ParallelLeafReader, SlowCodecReaderWrapper, IndexWriter.addIndexes,
 * MergePolicy.OneMerge.wrapForMerge). The default codec is pinned so the default stored-fields
 * merge path is exercised.
 */
public class TestParallelLeafReaderCopyStoredFieldNames extends LuceneTestCase {

  private static IndexWriterConfig defaultConfig() {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    return iwc;
  }

  /** Index A: one doc with stored "color". Field numbers in A: color=0. */
  private static void buildA(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, defaultConfig())) {
      Document d = new Document();
      d.add(new StoredField("color", "red"));
      w.addDocument(d);
      w.forceMerge(1);
    }
  }

  /** Index B: one doc with stored "weight" (int) and "shape". Field numbers in B: weight=0, shape=1. */
  private static void buildB(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, defaultConfig())) {
      Document d = new Document();
      d.add(new StoredField("weight", 7));
      d.add(new StoredField("shape", "circle"));
      w.addDocument(d);
      w.forceMerge(1);
    }
  }

  /** name -> list of values (as strings), in visit order. */
  private static Map<String, List<String>> fieldsOf(Document doc) {
    Map<String, List<String>> m = new TreeMap<>();
    for (IndexableField f : doc.getFields()) {
      String v = f.stringValue() != null ? f.stringValue() : String.valueOf(f.numericValue());
      m.computeIfAbsent(f.name(), k -> new ArrayList<>()).add(v);
    }
    return m;
  }

  private static final String EXPECTED = "{color=[red], shape=[circle], weight=[7]}";

  private void copyAndCheck(boolean shiftTargetNumbering) throws IOException {
    try (Directory dirA = newDirectory();
        Directory dirB = newDirectory();
        Directory target = newDirectory()) {
      buildA(dirA);
      buildB(dirB);
      try (DirectoryReader ra = DirectoryReader.open(dirA);
          DirectoryReader rb = DirectoryReader.open(dirB)) {
        ParallelLeafReader plr =
            new ParallelLeafReader(false, getOnlyLeafReader(ra), getOnlyLeafReader(rb));
        try {
          // Sanity: the parallel reader itself returns the union of the stored fields by name.
          assertEquals(
              "ParallelLeafReader view", EXPECTED, fieldsOf(plr.storedFields().document(0)).toString());

          try (IndexWriter w = new IndexWriter(target, defaultConfig())) {
            if (shiftTargetNumbering) {
              // Control: target already knows an unrelated field, so its numbering differs from the
              // parallel reader's and the merge must remap by name.
              Document other = new Document();
              other.add(new StoredField("unrelated", "x"));
              w.addDocument(other);
              w.commit();
            }
            w.addIndexes(SlowCodecReaderWrapper.wrap(plr));
            w.forceMerge(1);
          }
        } finally {
          plr.close();
        }
      }
      // The copy is structurally valid: CheckIndex does not flag the renamed values.
      TestUtil.checkIndex(target);
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        LeafReader leaf = getOnlyLeafReader(copy);
        int docId = shiftTargetNumbering ? 1 : 0;
        assertEquals(shiftTargetNumbering ? 2 : 1, leaf.maxDoc());
        assertEquals(
            "stored fields of the addIndexes copy (shiftTargetNumbering=" + shiftTargetNumbering + ")",
            EXPECTED,
            fieldsOf(leaf.storedFields().document(docId)).toString());
      }
    }
  }

  /** addIndexes(SlowCodecReaderWrapper.wrap(parallelReader)) into a fresh index. */
  public void testAddIndexesFreshTarget() throws IOException {
    copyAndCheck(false);
  }

  /** Control: same copy, but the target's field numbering differs, so remapping is used. */
  public void testAddIndexesShiftedTargetControl() throws IOException {
    copyAndCheck(true);
  }

  /**
   * The "add a field on merge" pattern: OneMerge.wrapForMerge returns a ParallelLeafReader of the
   * segment plus a freshly built parallel index carrying an extra stored field.
   */
  public void testWrapForMergeAddsStoredField() throws IOException {
    final List<Closeable2> toClose = new ArrayList<>();
    MergePolicy mp =
        new FilterMergePolicy(new LogDocMergePolicy()) {
          @Override
          public MergeSpecification findMerges(
              MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext) {
            return null; // only the forced merge below
          }

          @Override
          public MergeSpecification findForcedMerges(
              SegmentInfos segmentInfos,
              int maxSegmentCount,
              Map<SegmentCommitInfo, Boolean> segmentsToMerge,
              MergeContext mergeContext)
              throws IOException {
            MergeSpecification spec =
                super.findForcedMerges(
                    segmentInfos, maxSegmentCount, segmentsToMerge, mergeContext);
            if (spec == null) {
              return null;
            }
            MergeSpecification wrapped = new MergeSpecification();
            for (OneMerge m : spec.merges) {
              wrapped.add(
                  new OneMerge(m.segments) {
                    @Override
                    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                      // Re-derive a parallel index for this segment: one doc per segment doc, in
                      // order, with a stored "note" computed from the segment's stored "key".
                      Directory pdir = newDirectory();
                      try (IndexWriter pw = new IndexWriter(pdir, defaultConfig())) {
                        StoredFields sf = reader.storedFields();
                        for (int i = 0; i < reader.maxDoc(); i++) {
                          Document src = sf.document(i);
                          Document d = new Document();
                          d.add(new StoredField("note", "note-" + src.get("key")));
                          pw.addDocument(d);
                        }
                        pw.forceMerge(1);
                      }
                      DirectoryReader pr = DirectoryReader.open(pdir);
                      toClose.add(new Closeable2(pr, pdir));
                      // closeSubReaders=true: the wrapper adds no references, so the segment
                      // reader's lifecycle stays with the writer; pr is closed after the merge.
                      return SlowCodecReaderWrapper.wrap(
                          new ParallelLeafReader(true, reader, getOnlyLeafReader(pr)));
                    }
                  });
            }
            return wrapped;
          }
        };

    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = defaultConfig();
      iwc.setMergePolicy(mp);
      iwc.setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // two segments; main index field numbers: key=0, score=1
        for (int seg = 0; seg < 2; seg++) {
          for (int i = 0; i < 3; i++) {
            Document d = new Document();
            d.add(new StoredField("key", "k" + seg + i));
            d.add(new StoredField("score", (long) (10 * seg + i)));
            w.addDocument(d);
          }
          w.commit();
        }
        w.forceMerge(1);
      } finally {
        for (Closeable2 c : toClose) {
          c.close();
        }
      }
      TestUtil.checkIndex(dir);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        assertEquals(6, leaf.maxDoc());
        StoredFields sf = leaf.storedFields();
        for (int i = 0; i < leaf.maxDoc(); i++) {
          Map<String, List<String>> got = fieldsOf(sf.document(i));
          String key = got.containsKey("key") ? got.get("key").get(0) : null;
          assertNotNull("doc " + i + " lost its key: " + got, key);
          String expected =
              "{key=[" + key + "], note=[note-" + key + "], score=" + got.get("score") + "}";
          assertEquals("stored fields after the wrapping merge, doc " + i, expected, got.toString());
        }
      }
    }
  }

  private static final class Closeable2 {
    final DirectoryReader reader;
    final Directory dir;

    Closeable2(DirectoryReader reader, Directory dir) {
      this.reader = reader;
      this.dir = dir;
    }

    void close() throws IOException {
      reader.close();
      dir.close();
    }
  }
}
