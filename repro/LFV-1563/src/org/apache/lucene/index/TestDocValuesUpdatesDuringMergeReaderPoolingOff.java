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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.SetOnce;

public class TestDocValuesUpdatesDuringMergeReaderPoolingOff extends LuceneTestCase {

  // Expected: all three documents read 9. Actual: forceMerge throws NoSuchFileException.
  public void testUpdatesWhileMergeRuns() throws Exception {
    SetOnce<IndexWriter> writer = new SetOnce<>();
    MergePolicy mergePolicy =
        new OneMergeWrappingMergePolicy(
            new TieredMergePolicy(),
            merge ->
                new MergePolicy.OneMerge(merge.segments) {
                  boolean updated;

                  @Override
                  public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                    if (updated == false) {
                      updated = true;
                      IndexWriter w = writer.get();
                      for (int value = 1; value <= 8; value++) {
                        w.updateNumericDocValue(new Term("id", "x"), "n", value);
                        w.flush();
                      }
                      w.addDocument(doc());
                      w.updateNumericDocValue(new Term("id", "x"), "n", 9);
                      w.flushNextBuffer();
                    }
                    return reader;
                  }
                });
    IndexWriterConfig config =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergeScheduler(new SerialMergeScheduler())
            .setMergePolicy(mergePolicy)
            .setReaderPooling(false);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config)) {
      writer.set(w);
      w.addDocument(doc());
      w.flush();
      w.addDocument(doc());
      w.flush();
      w.forceMerge(1);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(
            3, new IndexSearcher(reader).count(NumericDocValuesField.newSlowExactQuery("n", 9)));
      }
    }
  }

  private static Document doc() {
    Document doc = new Document();
    doc.add(new StringField("id", "x", Field.Store.NO));
    doc.add(new NumericDocValuesField("n", 0));
    return doc;
  }
}
