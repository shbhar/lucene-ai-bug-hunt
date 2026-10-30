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
package org.apache.lucene.benchmark.quality;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQualityPackageSample extends LuceneTestCase {

  // Expected: the package overview's execute call compiles; actual: javac finds no execute(int, ...).
  public void testOverviewExecuteCallCompiles() throws Exception {
    Path dir = createTempDir();
    Path src = dir.resolve("Sample.java");
    Files.writeString(
        src,
        """
        import java.io.PrintWriter;
        import org.apache.lucene.benchmark.quality.*;
        import org.apache.lucene.benchmark.quality.utils.SubmissionReport;
        class Sample {
          QualityStats[] run(QualityBenchmark qrun, Judge judge, PrintWriter logger) throws Exception {
            int maxResults = 1000;
            SubmissionReport submitLog = null;
            QualityStats stats[] = qrun.execute(maxResults, judge, submitLog, logger);
            return stats;
          }
        }
        """);
    int rc =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null, null, null, "-proc:none", "-cp", System.getProperty("java.class.path"), "-d",
                dir.toString(), src.toString());
    assertEquals(0, rc);
  }
}
