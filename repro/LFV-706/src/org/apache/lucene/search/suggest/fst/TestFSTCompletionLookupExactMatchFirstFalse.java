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

import java.io.StringReader;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFSTCompletionLookupExactMatchFirstFalse extends LuceneTestCase {

  public void testExactMatchNotPromotedAfterBuild() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "fst", 10, false);
      lookup.build(new FileDictionary(new StringReader("star\t1\nstar wars\t100\n")));
      // exactMatchFirst=false: by discretized weight, no promotion; actual: [star/0, star wars/5]
      assertEquals("[star wars/5, star/0]", lookup.lookup("star", true, 2).toString());
    }
  }
}
