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
package org.apache.lucene.analysis.compound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDictionaryCompoundFactoryRemovedKeyWarning extends LuceneTestCase {

  public void testNoWarningWhenRemovedKeyIsAbsent() {
    List<String> logged = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
              logged.add(r.getMessage());
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Logger log = Logger.getLogger(DictionaryCompoundWordTokenFilterFactory.class.getName());
    log.addHandler(handler);
    try {
      new DictionaryCompoundWordTokenFilterFactory(new HashMap<>(Map.of("dictionary", "d.txt")));
    } finally {
      log.removeHandler(handler);
    }
    // expected: no WARNING, since onlyLongestMatch was never passed
    assertEquals(List.of(), logged);
  }
}
