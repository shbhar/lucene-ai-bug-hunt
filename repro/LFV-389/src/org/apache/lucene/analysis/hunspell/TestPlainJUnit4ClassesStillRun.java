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
package org.apache.lucene.analysis.hunspell;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.monitor.outsidepackage.TestCandidateMatcherVisibility;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * TestHunspellRepositoryTestCases (@RunWith(Parameterized.class)) and
 * TestCandidateMatcherVisibility (no runner, no superclass) are plain JUnit 4 classes, not
 * LuceneTestCase/RandomizedRunner classes, so their test methods are only found through @Test.
 * The copies next to this file are verbatim from main. Each class must run at least one test and
 * pass. (Plain JUnit 4 on purpose: it sets a system property.)
 */
public class TestPlainJUnit4ClassesStillRun {

  private static String describe(Result r) {
    StringBuilder sb = new StringBuilder("run=" + r.getRunCount() + " failures=" + r.getFailureCount());
    for (Failure f : r.getFailures()) sb.append("\n  ").append(f.getTestHeader()).append(": ").append(f.getMessage());
    return sb.toString();
  }

  @Test
  public void testHunspellRepositoryTestCasesRuns() throws Exception {
    // a minimal "Hunspell repository" with one passing fixture
    Path repo = Files.createTempDirectory("hunspell-repo");
    Path tests = Files.createDirectories(repo.resolve("tests"));
    Files.writeString(tests.resolve("tiny.aff"), "SET UTF-8\n");
    Files.writeString(tests.resolve("tiny.dic"), "1\nfoo\n");
    Files.writeString(tests.resolve("tiny.good"), "foo\n");
    String old = System.getProperty("hunspell.repo.path");
    System.setProperty("hunspell.repo.path", repo.toString());
    try {
      Result r = new JUnitCore().run(TestHunspellRepositoryTestCases.class);
      assertTrue(describe(r), r.getRunCount() > 0 && r.wasSuccessful());
    } finally {
      if (old == null) System.clearProperty("hunspell.repo.path");
      else System.setProperty("hunspell.repo.path", old);
    }
  }

  @Test
  public void testCandidateMatcherVisibilityRuns() {
    Result r = new JUnitCore().run(TestCandidateMatcherVisibility.class);
    assertTrue(describe(r), r.getRunCount() > 0 && r.wasSuccessful());
  }
}
