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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestTieredMergePolicySubByteFloor extends LuceneTestCase {

  public void testCommitWithSubByteFloor() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(new TieredMergePolicy().setFloorSegmentMB(1e-9));
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 10_000; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", i == 0 ? "keep" : "del", Field.Store.NO));
      w.addDocument(doc);
    }
    w.deleteDocuments(new Term("id", "del"));
    Thread committer =
        new Thread(
            () -> {
              try {
                w.commit();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    committer.setDaemon(true);
    committer.start();
    committer.join(10_000);
    // Expected: commit() returns; actual: it spins forever in TieredMergePolicy.findMerges.
    if (committer.isAlive()) {
      fail("commit() still running after 10 s at " + committer.getStackTrace()[0]);
    }
    w.close();
    dir.close();
  }
}
