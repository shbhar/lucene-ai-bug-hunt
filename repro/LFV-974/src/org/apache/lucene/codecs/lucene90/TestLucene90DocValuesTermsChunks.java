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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestLucene90DocValuesTermsChunks extends LuceneTestCase {

  public void testTermsWrittenInChunksOf16() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 17; i++) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("f", new BytesRef(term(i))));
          w.addDocument(doc);
        }
      }
      String dvd = Arrays.stream(dir.listAll()).filter(f -> f.endsWith(".dvd")).findFirst().get();
      byte[] bytes = new byte[(int) dir.fileLength(dvd)];
      try (IndexInput in = dir.openInput(dvd, IOContext.READONCE)) {
        in.readBytes(bytes, 0, bytes.length);
      }
      String data = new String(bytes, StandardCharsets.ISO_8859_1);
      List<Integer> inFull = new ArrayList<>();
      for (int ord = 0; ord < 17; ord++) {
        if (data.contains((char) term(ord).length() + term(ord))) {
          inFull.add(ord);
        }
      }
      // javadoc: "written in chunks of 16, with the first value written completely" -> ord 16 in full
      assertTrue("expected ords 0 and 16 written completely, actual: " + inFull, inFull.contains(16));
    }
  }

  private static String term(int i) {
    return String.format(Locale.ROOT, "term%02d", i);
  }
}
