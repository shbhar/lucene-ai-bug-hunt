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
package org.apache.lucene.misc;

import org.apache.lucene.misc.IndexMergeTool.Options;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexMergeToolDoubleDash extends LuceneTestCase {

  public void testDoubleDashEndsOptions() throws Exception {
    // expected: "--" ends the options (target "target"); actual: "unrecognized option: '--'"
    Options options = Options.parse(new String[] {new String("--"), "target", "s1", "s2"});
    assertEquals("target", options.mergedIndexPath);
  }
}
