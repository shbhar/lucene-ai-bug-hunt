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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ResourceLoader;

public class TestICUTokenizerFactoryRuleFileClosed extends LuceneTestCase {

  public void testRuleFileClosedWhenNotUtf8() throws Exception {
    byte[] latin1 = "# café\n.+ {200};\n".getBytes(StandardCharsets.ISO_8859_1);
    AtomicBoolean closed = new AtomicBoolean();
    ResourceLoader loader =
        new ResourceLoader() {
          @Override
          public InputStream openResource(String resource) {
            return new ByteArrayInputStream(latin1) {
              @Override
              public void close() {
                closed.set(true);
              }
            };
          }

          @Override
          public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
            throw new UnsupportedOperationException();
          }
        };
    ICUTokenizerFactory factory =
        new ICUTokenizerFactory(new HashMap<>(Map.of("rulefiles", "Latn:latin.rbbi")));
    expectThrows(CharacterCodingException.class, () -> factory.inform(loader));
    assertTrue(
        "expected inform() to close the rule file stream it opened after failing; actual: left open",
        closed.get());
  }
}
