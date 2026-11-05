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
package org.apache.lucene.codecs.lucene90;

import java.util.Arrays;
import org.apache.lucene.codecs.CompoundDirectory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCompoundListAllSorted extends LuceneTestCase {

  public void testListAllIsSorted() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(true);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(new Document());
      }
      SegmentInfo si = SegmentInfos.readLatestCommit(dir).info(0).info;
      try (CompoundDirectory cfs = si.getCodec().compoundFormat().getCompoundReader(dir, si)) {
        String[] names = cfs.listAll();
        String[] sorted = names.clone();
        Arrays.sort(sorted);
        // expected: listAll() sorted as Directory.listAll requires; actual: HashMap order
        assertArrayEquals(Arrays.toString(names), sorted, names);
      }
    }
  }
}
