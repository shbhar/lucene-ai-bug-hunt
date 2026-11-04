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
package org.apache.lucene.analysis.query;

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryAutoStopWordAnalyzerDeletes extends LuceneTestCase {

  public void testUpdatedDocumentsDoNotCreateStopWords() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        Analyzer analyzer = new WhitespaceAnalyzer()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter writer = new IndexWriter(dir, iwc)) {
        for (String body : new String[] {"w", "x", "y", "w", "x", "y"}) {
          Document doc = new Document();
          doc.add(new TextField("body", body, Field.Store.NO));
          writer.updateDocument(new Term("body", body), doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir);
          QueryAutoStopWordAnalyzer qa = new QueryAutoStopWordAnalyzer(analyzer, reader)) {
        // each term is in 33% of docs (1/3 live, 2/6 with deleted), under the default 40%: expected [], actual [w, x, y]
        assertEquals(List.of(), Arrays.asList(qa.getStopWords("body")));
      }
    }
  }
}
