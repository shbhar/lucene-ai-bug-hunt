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
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFilterCodecReaderCloseKeepsReferencedReader extends LuceneTestCase {

  public void testClosingWrapperKeepsReferencedSegmentSearchable() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, config)) {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CodecReader segment = (CodecReader) reader.leaves().get(0).reader();
        segment.incRef();
        new FilterCodecReader(segment) {
          @Override
          public CacheHelper getCoreCacheHelper() {
            return null;
          }

          @Override
          public CacheHelper getReaderCacheHelper() {
            return null;
          }
        }.close();
        // expected 1: the open DirectoryReader still references the segment; actual 0
        assertEquals(
            "id:1 hits in the open DirectoryReader after closing a FilterCodecReader",
            1,
            new IndexSearcher(reader).count(new TermQuery(new Term("id", "1"))));
      }
    }
  }
}
