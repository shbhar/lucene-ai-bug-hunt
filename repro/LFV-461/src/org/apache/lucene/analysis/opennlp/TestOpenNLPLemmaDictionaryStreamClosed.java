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
package org.apache.lucene.analysis.opennlp;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ResourceLoader;

/**
 * Every InputStream an analysis factory opens through its ResourceLoader must be closed (the rule
 * LUCENE-8188 restored for OpenNLPOpsFactory). The lemma dictionary stream is left open.
 */
public class TestOpenNLPLemmaDictionaryStreamClosed extends BaseTokenStreamTestCase {

  public void testLemmaDictionaryStreamIsClosed() throws IOException {
    // a dictionary name no other test uses, so the JVM-wide model cache does not skip the load
    String dictName = "lemmas-" + getClass().getSimpleName() + ".dict";
    byte[] dict = "walks\tVBZ\twalk\nwalked\tVBD\twalk\n".getBytes(StandardCharsets.UTF_8);
    List<String> opened = new ArrayList<>();
    List<String> closed = new ArrayList<>();
    ResourceLoader loader =
        new ResourceLoader() {
          @Override
          public InputStream openResource(String resource) throws IOException {
            if (!resource.equals(dictName)) throw new IOException("no such resource: " + resource);
            opened.add(resource);
            return new FilterInputStream(new ByteArrayInputStream(dict)) {
              @Override
              public void close() throws IOException {
                closed.add(resource);
                super.close();
              }
            };
          }

          @Override
          public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
            throw new UnsupportedOperationException();
          }
        };
    try (Analyzer a =
        CustomAnalyzer.builder(loader)
            .withTokenizer("whitespace")
            .addTokenFilter("openNlpLemmatizer", "dictionary", dictName)
            .build()) {
      assertEquals("streams opened", List.of(dictName), opened);
      assertTrue("streams opened " + opened + " but closed " + closed, closed.containsAll(opened));
    }
  }
}
