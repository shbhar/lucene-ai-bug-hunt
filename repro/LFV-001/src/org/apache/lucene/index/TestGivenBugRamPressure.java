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
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * No explicit flush(): the doc-values update is resolved by an automatic (RAM-triggered) apply of
 * a frozen packet, and written by IndexWriter#writeSomeDocValuesUpdates (reader-pool RAM pressure),
 * which uses checkpointNoSIS(). Afterwards nothing is pending and the version has not moved.
 */
public class TestGivenBugRamPressure extends LuceneTestCase {

  public void testWriteSomeDocValuesUpdatesUnderRamPressure() throws IOException {
    final int numDocs = 200_000;
    try (Directory dir = new ByteBuffersDirectory()) {
      // build one big segment with a roomy buffer
      try (IndexWriter w0 =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setRAMBufferSizeMB(256)
                  .setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int i = 0; i < numDocs; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
          doc.add(new StringField("key", "all", Field.Store.NO));
          doc.add(new NumericDocValuesField("val", 1));
          w0.addDocument(doc);
        }
        w0.commit();
      }
      AtomicInteger writeSomeMessages = new AtomicInteger();
      InfoStream counting =
          new InfoStream() {
            @Override
            public void message(String component, String message) {
              if (message.startsWith("now write some pending DV updates")) {
                writeSomeMessages.incrementAndGet();
              }
            }

            @Override
            public boolean isEnabled(String component) {
              return "BD".equals(component);
            }

            @Override
            public void close() {}
          };
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setRAMBufferSizeMB(0.5)
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setMergeScheduler(new SerialMergeScheduler())
              .setInfoStream(counting);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        DirectoryReader r = DirectoryReader.open(w);
        assertEquals(1, r.leaves().size());
        // one update that resolves to all 200k docs (a big resolved DV packet in the reader pool)
        w.updateNumericDocValue(new Term("key", "all"), "val", 2);
        // then many updates that match nothing, so the delete queue is frozen and applied
        // automatically several times (RAM-triggered), without any explicit flush/getReader
        for (int i = 0; i < 100_000; i++) {
          w.updateNumericDocValue(new Term("id", "nope-" + i), "val", 3);
        }
        boolean current = r.isCurrent();
        DirectoryReader r2 = DirectoryReader.openIfChanged(r, w);
        DirectoryReader seen = r2 == null ? r : r2;
        NumericDocValues v = seen.leaves().get(0).reader().getNumericDocValues("val");
        assertTrue(v.advanceExact(0));
        long seenValue = v.longValue();
        long freshValue;
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          NumericDocValues fv = fresh.leaves().get(0).reader().getNumericDocValues("val");
          assertTrue(fv.advanceExact(0));
          freshValue = fv.longValue();
        }
        if (r2 != null) r2.close();
        r.close();
        System.out.println(
            "RAM-pressure probe: writeSomeDocValuesUpdates messages="
                + writeSomeMessages.get()
                + " isCurrent(before reopen)="
                + current
                + " openIfChanged="
                + (r2 == null ? "null" : "newReader")
                + " seen="
                + seenValue
                + " fresh="
                + freshValue);
        assertEquals("fresh reader must see the update", 2, freshValue);
        assertEquals(
            "openIfChanged/old reader must see the update (openIfChanged returned "
                + (r2 == null ? "null" : "new reader")
                + ", writeSome messages="
                + writeSomeMessages.get()
                + ")",
            freshValue,
            seenValue);
      }
    }
  }
}
