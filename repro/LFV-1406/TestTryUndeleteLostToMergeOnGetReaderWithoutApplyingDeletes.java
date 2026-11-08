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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.InfoStream;

public class TestTryUndeleteLostToMergeOnGetReaderWithoutApplyingDeletes extends LuceneTestCase {

  public void testUndeleteSurvivesMergeOnGetReaderWithApplyAllDeletesFalse() throws Exception {
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    InfoStream parkOther =
        new InfoStream() {
          @Override
          public void message(String component, String message) {
            if (Thread.currentThread().getName().equals("other")
                && message.startsWith("done inner apply del packet")) {
              held.countDown();
              try {
                release.await(60, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public boolean isEnabled(String component) {
            return component.equals("BD");
          }

          @Override
          public void close() {}
        };
    MergePolicy mergeFirstOnGetReader =
        new FilterMergePolicy(NoMergePolicy.INSTANCE) {
          @Override
          public MergeSpecification findFullFlushMerges(
              MergeTrigger trigger, SegmentInfos infos, MergeContext context) {
            if (trigger != MergeTrigger.GET_READER || infos.size() < 2) {
              return null;
            }
            MergeSpecification spec = new MergeSpecification();
            spec.add(new OneMerge(infos.asList().subList(0, 1)));
            return spec;
          }
        };
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setSoftDeletesField("soft")
            .setMergePolicy(mergeFirstOnGetReader)
            .setMergeScheduler(new SerialMergeScheduler())
            .setInfoStream(parkOther);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a"));
      w.addDocument(doc("b"));
      w.flush();
      w.updateDocValues(new Term("id", "a"), new NumericDocValuesField("soft", 1));
      Thread other =
          new Thread(
              () -> {
                try {
                  w.deleteDocuments(new Term("id", "nothing"));
                  w.addDocument(doc("c"));
                  w.flushNextBuffer();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              "other");
      long seqNo;
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        other.start();
        assertTrue(held.await(60, TimeUnit.SECONDS));
        seqNo = w.tryUpdateDocValue(reader, 0, new NumericDocValuesField("soft", null));
        DirectoryReader.open(w, false, false).close();
      } finally {
        release.countDown();
        other.join();
      }
      assertTrue(seqNo > 0);
      w.commit();
      try (DirectoryReader r =
          new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft")) {
        // expected: the acknowledged un-delete of "a" is in the commit; actual: "a" is gone (0)
        assertEquals(1, new IndexSearcher(r).count(new TermQuery(new Term("id", "a"))));
      }
    }
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    return doc;
  }
}
