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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Copying a ParallelLeafReader into an index (IndexWriter.addIndexes over SlowCodecReaderWrapper,
 * or a merge whose OneMerge.wrapForMerge returns one) must keep every stored field under its own
 * name. Both indexes below are built as the ParallelLeafReader javadoc requires (same documents in
 * the same order, different fields). Uses only public API.
 */
public class TestParallelReaderCopyKeepsStoredFieldNames extends LuceneTestCase {

  private static void addDocs(Directory dir, String field, String... values) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String v : values) {
        Document doc = new Document();
        doc.add(new StoredField(field, v));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
  }

  /** addIndexes(SlowCodecReaderWrapper.wrap(new ParallelLeafReader(a, b))) into an empty index. */
  public void testAddIndexesOfParallelReader() throws Exception {
    try (Directory dirA = newDirectory();
        Directory dirB = newDirectory();
        Directory target = newDirectory()) {
      addDocs(dirA, "title", "Lucene in Action");
      addDocs(dirB, "price", "42");
      try (DirectoryReader a = DirectoryReader.open(dirA);
          DirectoryReader b = DirectoryReader.open(dirB)) {
        ParallelLeafReader parallel =
            new ParallelLeafReader(false, getOnlyLeafReader(a), getOnlyLeafReader(b));
        // the parallel reader itself is correct:
        Document viaParallel = parallel.storedFields().document(0);
        assertEquals("Lucene in Action", viaParallel.get("title"));
        assertEquals("42", viaParallel.get("price"));
        try (IndexWriter w = new IndexWriter(target, new IndexWriterConfig())) {
          w.addIndexes(SlowCodecReaderWrapper.wrap(parallel));
        }
        parallel.close();
      }
      TestUtil.checkIndex(target); // the copy passes CheckIndex: nothing flags the renamed field
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        Document doc = copy.storedFields().document(0);
        assertEquals("stored fields of the copy: " + doc, "42", doc.get("price"));
        assertEquals("stored fields of the copy: " + doc, 1, doc.getValues("title").length);
      }
    }
  }

  /** The same through a merge that wraps each segment with a parallel index (OneMerge API). */
  public void testMergeThatWrapsSegmentsWithParallelReader() throws Exception {
    try (Directory main = newDirectory();
        Directory side = newDirectory()) {
      addDocs(side, "summary", "summary0", "summary1");
      try (DirectoryReader sideReader = DirectoryReader.open(side)) {
        LeafReader sideLeaf = getOnlyLeafReader(sideReader);
        AtomicBoolean merged = new AtomicBoolean();
        IndexWriterConfig iwc = new IndexWriterConfig();
        iwc.setMergePolicy(
            new FilterMergePolicy(new LogDocMergePolicy()) {
              @Override
              public MergeSpecification findForcedMerges(
                  SegmentInfos infos,
                  int maxSegmentCount,
                  Map<SegmentCommitInfo, Boolean> segmentsToMerge,
                  MergeContext mergeContext) {
                if (merged.getAndSet(true)) {
                  return null;
                }
                List<SegmentCommitInfo> segments = new ArrayList<>();
                infos.forEach(segments::add);
                MergeSpecification spec = new MergeSpecification();
                spec.add(
                    new OneMerge(segments) {
                      final List<ParallelLeafReader> parallelReaders = new ArrayList<>();

                      @Override
                      public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                        ParallelLeafReader parallel = new ParallelLeafReader(false, reader, sideLeaf);
                        parallelReaders.add(parallel);
                        return SlowCodecReaderWrapper.wrap(parallel);
                      }

                      @Override
                      public void mergeFinished(boolean success, boolean segmentDropped)
                          throws IOException {
                        super.mergeFinished(success, segmentDropped);
                        for (ParallelLeafReader parallel : parallelReaders) {
                          parallel.close();
                        }
                      }
                    });
                return spec;
              }
            });
        try (IndexWriter w = new IndexWriter(main, iwc)) {
          for (int i = 0; i < 2; i++) {
            Document doc = new Document();
            doc.add(new StoredField("id", "id" + i));
            doc.add(new StoredField("body", "body" + i));
            w.addDocument(doc);
          }
          w.commit();
          w.forceMerge(1);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(main)) {
        Document doc = r.storedFields().document(0);
        assertEquals("stored fields after the merge: " + doc, "summary0", doc.get("summary"));
        assertEquals("stored fields after the merge: " + doc, 1, doc.getValues("id").length);
      }
    }
  }
}
