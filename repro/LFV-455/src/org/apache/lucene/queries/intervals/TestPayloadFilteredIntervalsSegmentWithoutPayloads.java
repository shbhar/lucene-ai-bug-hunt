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
package org.apache.lucene.queries.intervals;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestPayloadFilteredIntervalsSegmentWithoutPayloads extends LuceneTestCase {

  public void testSegmentWithoutPayloads() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      Token withPayload = new Token("dog", 0, 3);
      withPayload.setPayload(new BytesRef("noun"));
      for (Token token : new Token[] {withPayload, new Token("dog", 0, 3)}) {
        Document doc = new Document();
        doc.add(new TextField("f", new CannedTokenStream(token)));
        w.addDocument(doc);
        w.commit(); // one segment per document; the second segment has no payloads for "f"
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IntervalQuery query = new IntervalQuery("f", Intervals.term("dog", p -> p != null));
        try {
          assertEquals(1, new IndexSearcher(reader).count(query));
        } catch (IllegalArgumentException e) {
          throw new AssertionError(
              "expected count 1 (the doc whose 'dog' has a payload), got " + e, e);
        }
      }
    }
  }
}
