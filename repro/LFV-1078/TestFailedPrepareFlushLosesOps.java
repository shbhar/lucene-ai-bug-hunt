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

import java.util.Collections;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.Monster;
import org.apache.lucene.util.BytesRef;

@Monster("buffers about 2.2 GB of doc-values updates; needs -Xmx3g")
public class TestFailedPrepareFlushLosesOps extends LuceneTestCase {

  public void testNextCommitKeepsAcknowledgedOps() throws Exception {
    Term victim = new Term("id", new BytesRef(new byte[1024]));
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      Document d = new Document();
      d.add(new StringField("id", victim.bytes(), Field.Store.NO));
      d.add(new NumericDocValuesField("n", 0));
      w.addDocument(d);
      w.commit();
      Term buffered = new Term("id", "buffered");
      w.addDocument(Collections.singletonList(new StringField("id", "buffered", Field.Store.NO)));
      for (int i = 1; i <= 2_200_000; i++) {
        w.updateNumericDocValue(victim, "n", i);
      }
      w.deleteDocuments(victim);
      expectThrows(ArithmeticException.class, w::commit);
      if (w.isOpen()) {
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          // expected buffered=1 victim=0 (or a closed writer); actual: open writer, both ops lost
          IndexSearcher s = new IndexSearcher(r);
          int b = s.count(new TermQuery(buffered)), v = s.count(new TermQuery(victim));
          assertEquals("buffered=1 victim=0", "buffered=" + b + " victim=" + v);
        }
        w.rollback();
      }
    }
  }
}
