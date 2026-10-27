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

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Same defect as TestHugeTokenClosesIndexWriter, reached through a stock analyzer: a TextField
 * analyzed with KeywordAnalyzer (KeywordTokenizer emits the whole value as one token, with no length
 * cap). Needs about 4.5 GB of heap.
 */
public class TestHugeKeywordTokenClosesIndexWriter extends LuceneTestCase {

  public void testKeywordAnalyzerHugeValueClosesWriter() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new KeywordAnalyzer()))) {
      Document ok = new Document();
      ok.add(new StringField("id", "buffered-before", Field.Store.YES));
      w.addDocument(ok);

      Document huge = new Document();
      huge.add(new TextField("body", "a".repeat(Integer.MAX_VALUE / 3 + 1), Field.Store.NO));
      Throwable thrown = null;
      try {
        w.addDocument(huge);
      } catch (Throwable t) {
        thrown = t;
      }
      huge = null;
      assertNotNull(thrown);
      assertTrue(
          "IndexWriter must stay open after an immense term; tragic exception = "
              + w.getTragicException(),
          w.isOpen());
      assertTrue("expected IllegalArgumentException, got " + thrown, thrown instanceof IllegalArgumentException);
    }
  }
}
