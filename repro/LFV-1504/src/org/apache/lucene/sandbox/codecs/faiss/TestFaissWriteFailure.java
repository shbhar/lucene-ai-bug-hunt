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
package org.apache.lucene.sandbox.codecs.faiss;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.FilterIndexOutput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.junit.BeforeClass;

public class TestFaissWriteFailure extends LuceneTestCase {

  private static boolean diskFull;

  @BeforeClass
  public static void maybeSuppress() {
    if (Boolean.getBoolean("tests.faiss.run")) {
      return;
    }
    boolean dependenciesPresent;
    try {
      FaissLibrary _ = FaissLibrary.INSTANCE;
      dependenciesPresent = true;
    } catch (LinkageError _) {
      dependenciesPresent = false;
    }
    assumeTrue("Dependencies present", dependenciesPresent);
  }

  public void testFailedIndexWriteIsReportedByCommit() throws Exception {
    Process child =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                TestFaissWriteFailure.class.desiredAssertionStatus() ? "-ea" : "-da",
                "--enable-native-access=ALL-UNNAMED",
                "-Djava.library.path=" + System.getProperty("java.library.path"),
                "-cp",
                System.getProperty("java.class.path"),
                TestFaissWriteFailure.class.getName())
            .redirectErrorStream(true)
            .start();
    String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    // expected: commit() throws and the child JVM ends normally; actual: it exits with code 1
    assertEquals("exit code of the child JVM, which printed:\n" + output, 0, child.waitFor());
  }

  public static void main(String[] args) throws IOException {
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexOutput createOutput(String name, IOContext context) throws IOException {
            return new FilterIndexOutput(name, name, super.createOutput(name, context)) {
              @Override
              public void writeBytes(byte[] b, int offset, int length) throws IOException {
                if (diskFull && name.endsWith(".faissd")) {
                  throw new IOException("no space left on device");
                }
                out.writeBytes(b, offset, length);
              }
            };
          }
        };
    IndexWriterConfig config =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new FaissKnnVectorsFormat()));
    IndexWriter writer = new IndexWriter(dir, config);
    writer.addDocument(List.of(new KnnFloatVectorField("vector", new float[] {1, 2})));
    diskFull = true;
    expectThrows(Exception.class, writer::commit);
    writer.rollback();
    dir.close();
  }
}
