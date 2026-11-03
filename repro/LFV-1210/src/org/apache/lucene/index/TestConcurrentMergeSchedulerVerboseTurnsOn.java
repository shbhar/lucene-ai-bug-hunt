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

import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

public class TestConcurrentMergeSchedulerVerboseTurnsOn extends LuceneTestCase {

  static final class TurnsOnInfoStream extends InfoStream {
    boolean armed, enabled;

    @Override
    public boolean isEnabled(String component) {
      boolean current = enabled;
      enabled = armed;
      return current;
    }

    @Override
    public void message(String component, String message) {}

    @Override
    public void close() {}
  }

  public void testLoggingTurnedOnDuringSetForceMergeMBPerSec() throws Exception {
    TurnsOnInfoStream infoStream = new TurnsOnInfoStream();
    ConcurrentMergeScheduler cms = new ConcurrentMergeScheduler();
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(
                dir, newIndexWriterConfig().setMergeScheduler(cms).setInfoStream(infoStream))) {
      infoStream.armed = true;
      // expected: returns normally (logging is now on); actual: NullPointerException in updateMergeThreads
      cms.setForceMergeMBPerSec(10.0);
    }
  }
}
