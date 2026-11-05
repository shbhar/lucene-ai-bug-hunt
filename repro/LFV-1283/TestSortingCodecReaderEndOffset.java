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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSortingCodecReaderEndOffset extends LuceneTestCase {

  public void testEndOffsetWithoutOffsets() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 2; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("id", i));
        doc.add(new TextField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        CodecReader sorted =
            SortingCodecReader.wrap(
                (CodecReader) getOnlyLeafReader(r),
                new Sort(new SortField("id", SortField.Type.LONG, true)));
        PostingsEnum pe = sorted.postings(new Term("f", "a"), PostingsEnum.OFFSETS);
        pe.nextDoc();
        pe.nextPosition();
        // field has no offsets: expected startOffset -1 and endOffset -1; actual endOffset is 0
        assertEquals(-1, pe.startOffset());
        assertEquals(-1, pe.endOffset());
      }
    }
  }
}
