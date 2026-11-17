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
package org.apache.lucene.search.suggest.fst;

import java.io.IOException;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFSTCompletionLookupFailedBuildOpenOutput extends LuceneTestCase {

  // expected: a failed build closes its temp outputs; actual: suggest_RefSorterRaw_0.tmp stays open
  public void testFailedBuildClosesTempOutputs() throws IOException {
    MockDirectoryWrapper tempDir = newMockDirectory();
    tempDir.setMaxSizeInBytes(1);
    FSTCompletionLookup lookup = new FSTCompletionLookup(tempDir, "suggest");

    expectThrows(IOException.class, () -> lookup.build(InputIterator.EMPTY));

    tempDir.close();
  }
}
