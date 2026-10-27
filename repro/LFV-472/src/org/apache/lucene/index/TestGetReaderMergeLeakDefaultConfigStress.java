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

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Default IndexWriterConfig (TieredMergePolicy, ConcurrentMergeScheduler, merge-on-refresh with
 * the default 500 ms wait). One thread keeps updating a few documents and committing; another
 * keeps refreshing an NRT reader. Then the writer is closed and the files that the last commit
 * does not reference are counted. Probabilistic (timing): this measures how often the leak occurs
 * without any custom hook; the deterministic repro is TestGetReaderMergeDroppedSegmentLeak.
 */
public class TestGetReaderMergeLeakDefaultConfigStress extends LuceneTestCase {

  public void testUpdatesWhileRefreshing() throws Exception {
    long millis = Long.getLong("stress.millis", 5000L);
    int ids = Integer.getInteger("stress.ids", 2);
    try (Directory dir = FSDirectory.open(createTempDir("stress"))) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      AtomicBoolean stop = new AtomicBoolean();
      Thread updater =
          new Thread(
              () -> {
                try {
                  int i = 0;
                  while (stop.get() == false) {
                    Document d = new Document();
                    String id = Integer.toString(i % ids);
                    d.add(new StringField("id", id, Field.Store.YES));
                    w.updateDocument(new Term("id", id), d);
                    if (++i % ids == 0) {
                      w.commit();
                    }
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      updater.start();
      DirectoryReader r = DirectoryReader.open(w);
      long end = System.currentTimeMillis() + millis;
      int refreshes = 0;
      while (System.currentTimeMillis() < end) {
        DirectoryReader r2 = DirectoryReader.openIfChanged(r);
        if (r2 != null) {
          r.close();
          r = r2;
          refreshes++;
        }
      }
      stop.set(true);
      updater.join();
      r.close();
      w.close();
      Set<String> files = new TreeSet<>(Arrays.asList(dir.listAll()));
      files.removeAll(SegmentInfos.readLatestCommit(dir).files(true));
      files.remove(IndexWriter.WRITE_LOCK_NAME);
      files.removeIf(f -> f.startsWith("extra")); // test-framework ExtrasFS files
      Set<String> segs = new TreeSet<>();
      for (String f : files) {
        segs.add(IndexFileNames.parseSegmentName(f));
      }
      System.out.println(
          "refreshes=" + refreshes + " leakedFiles=" + files.size() + " leakedSegments=" + segs);
      assertEquals("files left behind after close()", Set.of(), files);
    }
  }
}
