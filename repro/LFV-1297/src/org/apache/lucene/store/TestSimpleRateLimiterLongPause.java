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
package org.apache.lucene.store;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ThreadInterruptedException;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestSimpleRateLimiterLongPause extends LuceneTestCase {

  public void testThreeDayPause() throws Exception {
    // expected: a 3-day pause asks Thread.sleep for at most 3 days; actual: Integer.MAX_VALUE ms (24.86 days)
    Path jfr = createTempFile("sleep", ".jfr");
    try (Recording recording = new Recording()) {
      recording.enable("jdk.ThreadSleep").withThreshold(Duration.ZERO);
      recording.start();
      Thread.currentThread().interrupt();
      RateLimiter limiter = new RateLimiter.SimpleRateLimiter(1.0);
      expectThrows(ThreadInterruptedException.class, () -> limiter.pause(3L * 24 * 3600 << 20));
      recording.dump(jfr);
    }
    long tid = Thread.currentThread().threadId();
    long sleptNS =
        RecordingFile.readAllEvents(jfr).stream()
            .filter(e -> e.getEventType().getName().equals("jdk.ThreadSleep"))
            .filter(e -> e.getThread() != null && e.getThread().getJavaThreadId() == tid)
            .mapToLong(e -> e.getDuration("time").toNanos())
            .max()
            .getAsLong();
    assertTrue("Thread.sleep asked for " + sleptNS + " ns", sleptNS <= TimeUnit.DAYS.toNanos(3));
  }
}
