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
package org.apache.lucene.codecs.uniformsplit.sharedterms;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSTUniformSplitMergeSegmentWithoutPostings extends LuceneTestCase {

  // Expected: forceMerge(1) merges the two segments; actual: NullPointerException in the merge.
  public void testMergeSegmentWithoutPostings() throws Exception {
    PostingsFormat sharedTerms = new STUniformSplitPostingsFormat();
    Codec codec =
        new FilterCodec("SharedTermsOnly", TestUtil.getDefaultCodec()) {
          @Override
          public PostingsFormat postingsFormat() {
            return sharedTerms;
          }
        };
    IndexWriterConfig config =
        new IndexWriterConfig().setCodec(codec).setMergeScheduler(new SerialMergeScheduler());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, config)) {
      Document indexed = new Document();
      indexed.add(new StringField("id", "a", Field.Store.NO));
      writer.addDocument(indexed);
      writer.flush();

      Document docValuesOnly = new Document();
      docValuesOnly.add(new NumericDocValuesField("n", 1));
      writer.addDocument(docValuesOnly);
      writer.flush();

      writer.forceMerge(1);

      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        assertEquals(1, reader.leaves().size());
        assertEquals(1, reader.docFreq(new Term("id", "a")));
      }
    }
  }
}
