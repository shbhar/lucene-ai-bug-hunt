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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexHnswGraphError extends LuceneTestCase {

  public void testBrokenHnswGraphIsNotReportedClean() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysKnnVectorsFormat(TestUtil.getDefaultKnnVectorsFormat()))
              .setUseCompoundFile(false);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 4; i++) {
          float[] v = new float[4];
          v[i] = 1;
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", v));
          w.addDocument(doc);
        }
      }
      String vex = Arrays.stream(dir.listAll()).filter(f -> f.endsWith(".vex")).findFirst().get();
      byte[] bytes;
      int node0SecondDelta;
      try (IndexInput in = dir.openInput(vex, IOContext.DEFAULT)) {
        node0SecondDelta = CodecUtil.readIndexHeader(in).length + 2;
        bytes = new byte[(int) in.length()];
        in.seek(0);
        in.readBytes(bytes, 0, bytes.length);
      }
      bytes[node0SecondDelta] = 0;
      dir.deleteFile(vex);
      try (IndexOutput out = dir.createOutput(vex, IOContext.DEFAULT)) {
        out.writeBytes(bytes, bytes.length - CodecUtil.footerLength());
        CodecUtil.writeFooter(out);
      }
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        checker.setThreadCount(1);
        checker.setInfoStream(new PrintStream(log, true, UTF_8));
        CheckIndex.Status status = checker.checkIndex();
        assertNotNull(log.toString(UTF_8), status.segmentInfos.get(0).hnswGraphsStatus.error);
        assertFalse(
            "expected clean=false after the HNSW graph test failed, got clean=true:\n"
                + log.toString(UTF_8),
            status.clean);
      }
    }
  }
}
