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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.lucene.tests.analysis.BaseTokenStreamFactoryTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.Version;

/**
 * History repro for the "onlyLongestMatch has been removed" warning of
 * DictionaryCompoundWordTokenFilterFactory. Version-neutral oracle, public API only (SPI lookup by
 * name, a string resource loader, java.util.logging capture by logger name):
 *
 * <ul>
 *   <li>a configuration that does not contain the key must not be told the key "has been removed";
 *   <li>whether the factory flags the key must not depend on the key's value (true vs false).
 * </ul>
 *
 * Releases without any such warning satisfy both (nothing is ever logged). Token output is not
 * asserted here.
 */
public class TestDictCompoundFactoryRemovedKeyWarning extends BaseTokenStreamFactoryTestCase {

  private static final String DICT = "donau\ndampf\ndampfschiff\nschiff\n";
  // strong reference so the JUL logger (and the handler added to it) cannot be collected
  private static final Logger FACTORY_LOG =
      Logger.getLogger(
          "org.apache.lucene.analysis.compound.DictionaryCompoundWordTokenFilterFactory");

  private static final class Capture extends Handler {
    final List<String> warnings = new ArrayList<>();

    @Override
    public synchronized void publish(LogRecord r) {
      if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
        warnings.add(String.valueOf(r.getMessage()));
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  /** Builds the factory through SPI; returns the WARNING messages that mention onlyLongestMatch. */
  private List<String> keyWarnings(String... extraArgs) throws Exception {
    String[] args = new String[2 + extraArgs.length];
    args[0] = "dictionary";
    args[1] = "dict.txt";
    System.arraycopy(extraArgs, 0, args, 2, extraArgs.length);
    Capture cap = new Capture();
    FACTORY_LOG.addHandler(cap);
    try {
      tokenFilterFactory(
          "dictionaryCompoundWord", Version.LATEST, new StringMockResourceLoader(DICT), args);
    } finally {
      FACTORY_LOG.removeHandler(cap);
    }
    List<String> out = new ArrayList<>();
    for (String w : cap.warnings) {
      if (w.contains("onlyLongestMatch")) {
        out.add(w);
      }
    }
    return out;
  }

  public void testNoRemovedKeyWarningWhenKeyAbsent() throws Exception {
    List<String> w = keyWarnings();
    assertTrue(
        "factory configured WITHOUT onlyLongestMatch logged a warning about it: " + w, w.isEmpty());
  }

  public void testKeyWarningDoesNotDependOnValue() throws Exception {
    List<String> wTrue = keyWarnings("onlyLongestMatch", "true");
    List<String> wFalse = keyWarnings("onlyLongestMatch", "false");
    assertEquals(
        "the onlyLongestMatch key is flagged for one value but not the other: true->"
            + wTrue
            + " false->"
            + wFalse,
        wTrue.isEmpty(),
        wFalse.isEmpty());
  }

  /** Always passes: prints which configurations log the warning, for the record. */
  public void testDiagnosticMatrix() throws Exception {
    String[][] cfgs = {
      {},
      {"onlyLongestMatch", "true"},
      {"onlyLongestMatch", "false"},
      {"onlyLongestMatchIgnoreSubwords", "true"},
      {"onlyLongestMatchIgnoreSubwords", "false"},
    };
    for (String[] c : cfgs) {
      try {
        System.out.println("HIST-MATRIX args=" + Arrays.toString(c) + " warnings=" + keyWarnings(c));
      } catch (IllegalArgumentException e) {
        System.out.println("HIST-MATRIX args=" + Arrays.toString(c) + " IAE: " + e.getMessage());
      }
    }
  }
}
