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
package org.apache.lucene.codecs.uniformsplit;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * UniformSplitTermsWriter.validateSettings accepts any 0 <= deltaNumLines < targetNumBlockLines.
 * With such a setting, flushing a field whose last block has between targetNumBlockLines -
 * deltaNumLines + 1 and 2 * deltaNumLines lines fails in BlockWriter.splitAndWriteBlock, whose
 * split window starts at numLines - 2 * deltaNumLines (asserted >= 1, else negative or 0).
 */
public class TestUniformSplitValidSettingsFlush extends LuceneTestCase {

  private void indexAndCheck(int targetNumBlockLines, int deltaNumLines, int numTerms)
      throws Exception {
    // the settings are valid according to the documented validation
    UniformSplitTermsWriter.validateSettings(targetNumBlockLines, deltaNumLines);
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(
        TestUtil.alwaysPostingsFormat(
            new UniformSplitPostingsFormat(targetNumBlockLines, deltaNumLines, null, null, false)));
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < numTerms; i++) {
          Document d = new Document();
          d.add(new StringField("f", String.format(java.util.Locale.ROOT, "t%03d", i), Field.Store.NO));
          w.addDocument(d);
        }
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        TermsEnum te = MultiTerms.getTerms(r, "f").iterator();
        int n = 0;
        while (te.next() != null) n++;
        assertEquals(numTerms, n);
      }
    }
  }

  public void testFlushWithValidSmallBlockSettings() throws Exception {
    List<String> errors = new ArrayList<>();
    int[][] cases = {{4, 2, 3}, {4, 2, 4}, {10, 4, 7}, {10, 4, 8}};
    for (int[] c : cases) {
      try {
        indexAndCheck(c[0], c[1], c[2]);
      } catch (Throwable t) {
        errors.add("targetNumBlockLines=" + c[0] + " deltaNumLines=" + c[1] + " terms=" + c[2] + ": " + t);
      }
    }
    assertTrue(String.join("\n", errors), errors.isEmpty());
  }
}
