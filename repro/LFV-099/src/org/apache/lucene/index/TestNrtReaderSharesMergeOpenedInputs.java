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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * An NRT reader opened while a merge runs shares the segment readers that the merge opened with
 * its MERGE IOContext (ReadersAndUpdates#getReaderForMerge opens the pooled reader with the
 * merge's context when no reader exists yet). Searches through the NRT reader then read the
 * stored fields, the vectors and the HNSW graph of those segments through inputs opened for the
 * merge; with MMapDirectory.ADVISE_BY_CONTEXT these are advised SEQUENTIAL (MERGE context), while
 * a search reader opens the same files RANDOM. #16684/#16749/#16883 give merges inputs of their
 * own precisely so that searches keep their random-access advice.
 */
public class TestNrtReaderSharesMergeOpenedInputs extends LuceneTestCase {

  /** Control: an NRT reader that exists before the merge opened the files for searching. */
  public void testControlNrtOpenedBeforeMerge() throws Exception {
    doTest(true);
  }

  public void testNrtDuringFirstMerge() throws Exception {
    doTest(false);
  }

  private void doTest(boolean nrtBeforeMerge) throws Exception {
    Map<String, List<IOContext.Context>> opens = new ConcurrentHashMap<>();
    // with MMapDirectory#setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT) a MERGE context means
    // SEQUENTIAL advice; the IOContext the files are opened with is what this test observes
    MMapDirectory mmap = new MMapDirectory(createTempDir());
    Directory dir =
        new FilterDirectory(mmap) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            opens.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(context.context());
            return super.openInput(name, context);
          }
        };
    CountDownLatch inMerge = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    MergePolicy mp =
        new FilterMergePolicy(newLogMergePolicy()) {
          @Override
          public MergeSpecification findForcedMerges(
              SegmentInfos infos,
              int max,
              Map<SegmentCommitInfo, Boolean> toMerge,
              MergeContext ctx)
              throws IOException {
            MergeSpecification spec = super.findForcedMerges(infos, max, toMerge, ctx);
            if (spec == null) {
              return null;
            }
            MergeSpecification wrapped = new MergeSpecification();
            for (OneMerge m : spec.merges) {
              wrapped.add(
                  new OneMerge(m.segments) {
                    @Override
                    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                      inMerge.countDown();
                      try {
                        assertTrue(release.await(30, TimeUnit.SECONDS));
                      } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                      }
                      return reader;
                    }
                  });
            }
            return wrapped;
          }
        };
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig()
                .setCodec(TestUtil.getDefaultCodec())
                .setUseCompoundFile(false)
                .setMergePolicy(mp)
                .setMergeScheduler(new SerialMergeScheduler()));
    for (int seg = 0; seg < 2; seg++) {
      for (int i = 0; i < 300; i++) {
        Document doc = new Document();
        doc.add(new StoredField("id", i));
        float[] v = new float[16];
        for (int d = 0; d < 16; d++) {
          v[d] = random().nextFloat();
        }
        doc.add(new KnnFloatVectorField("vec", v, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
      w.commit();
    }
    DirectoryReader early = nrtBeforeMerge ? DirectoryReader.open(w) : null;
    if (nrtBeforeMerge == false) {
      opens.clear();
    }
    Thread merger =
        new Thread(
            () -> {
              try {
                w.forceMerge(1);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    merger.start();
    assertTrue(inMerge.await(30, TimeUnit.SECONDS));
    DirectoryReader nrt;
    try {
      nrt = DirectoryReader.open(w);
    } finally {
      release.countDown();
      merger.join();
    }
    try {
      System.out.println("opens during the merge and the NRT open: " + opens);
      assertEquals(2 + 0, nrt.leaves().size()); // the two segments being merged
      for (String file :
          new String[] {
            "_0.fdt", "_0_Lucene99HnswVectorsFormat_0.vec", "_0_Lucene99HnswVectorsFormat_0.vex"
          }) {
        List<IOContext.Context> contexts = opens.get(file);
        assertNotNull(file + " was opened", contexts);
        // the NRT reader's searches must read through an input opened for searching
        assertTrue(
            "the NRT reader reads " + file + " only through inputs opened with " + contexts,
            contexts.stream().anyMatch(c -> c != IOContext.Context.MERGE));
      }
    } finally {
      nrt.close();
      if (early != null) {
        early.close();
      }
      w.close();
      dir.close();
    }
  }
}
