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
package org.apache.lucene.search.suggest.document;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCompletionMixedTypesMerge extends LuceneTestCase {

  public void testMergeOfCommittedSuggestAndContextSuggestSegments() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()))
            .setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document suggest = new Document();
      suggest.add(new SuggestField("suggest", "apple", 1));
      w.addDocument(suggest);
      w.commit();
      Document contextSuggest = new Document();
      contextSuggest.add(new ContextSuggestField("suggest", "banana", 1));
      w.addDocument(contextSuggest);
      w.commit();
      try {
        w.forceMerge(1);
      } catch (IllegalArgumentException ignored) {
      }
      // expected: the writer stays open (rejecting the merge is fine); actual: the merge closes it
      assertNull("the merge closed the writer", w.getTragicException());
    }
  }
}
