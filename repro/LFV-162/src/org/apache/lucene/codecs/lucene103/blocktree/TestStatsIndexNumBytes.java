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
package org.apache.lucene.codecs.lucene103.blocktree;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Stats#indexNumBytes is documented as "Byte size of the index", but the Lucene103 block tree
 * never sets it, so FieldReader#getStats() and CheckIndex's verbose output report a 0-byte terms
 * index for every field. Public API only (FieldReader and Stats are public).
 */
public class TestStatsIndexNumBytes extends LuceneTestCase {

  private static void indexIds(Directory dir) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 10000; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", String.format("%06d", i), Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
  }

  public void testGetStatsIndexNumBytes() throws Exception {
    try (Directory dir = newDirectory()) {
      indexIds(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Terms terms = reader.leaves().get(0).reader().terms("id");
        assertTrue(terms instanceof FieldReader);
        Stats stats = ((FieldReader) terms).getStats();
        assertTrue("several hundred blocks expected", stats.totalBlockCount > 100);
        // the trie that indexes these blocks occupies bytes in the .tip file
        assertTrue("indexNumBytes=" + stats.indexNumBytes, stats.indexNumBytes > 0);
      }
    }
  }

  public void testCheckIndexVerboseReportsTrieSize() throws Exception {
    try (Directory dir = newDirectory()) {
      indexIds(dir);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8), true);
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS); // -level 2: postings checks
        checker.setThreadCount(1);
        assertTrue(checker.checkIndex().clean);
      }
      String out = bos.toString(StandardCharsets.UTF_8);
      int at = out.indexOf("index trie:");
      assertTrue("CheckIndex -verbose prints the block-tree stats", at >= 0);
      String line = out.substring(at, out.indexOf(" bytes", at)).replace('\n', ' ').trim();
      assertFalse("CheckIndex -verbose reports: " + line + " bytes", line.endsWith(" 0"));
    }
  }
}
