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

/**
 * IndexMergeTool.Options.parse has an explicit branch that stops option parsing at "--", but it
 * compares the argument with == so the branch never runs for a command-line argument (and, if it
 * ran, it would leave "--" as the merged-index path). Uses the same package-private entry point
 * as the upstream TestIndexMergeTool.
 */
public class TestIndexMergeToolEndOfOptions extends LuceneTestCase {

  /** Control: plain positional arguments parse. */
  public void testNoOptionsControl() throws Exception {
    Options options = Options.parse(new String[] {"target", "source1", "source2"});
    assertEquals("target", options.mergedIndexPath);
  }

  /** "--" (built at run time, as the JVM does for argv) ends the options. */
  public void testDoubleDashEndsOptions() throws Exception {
    String dashDash = new String(new char[] {'-', '-'});
    Options options = Options.parse(new String[] {dashDash, "target", "source1", "source2"});
    assertEquals("target", options.mergedIndexPath);
    assertArrayEquals(new String[] {"source1", "source2"}, options.indexPaths);
  }

  /** After "--", a path that starts with '-' is a path, not an option. */
  public void testDoubleDashAllowsDashPrefixedPath() throws Exception {
    String dashDash = new String(new char[] {'-', '-'});
    Options options =
        Options.parse(new String[] {"-verbose", dashDash, "-target", "source1", "source2"});
    assertEquals("-target", options.mergedIndexPath);
  }

  /** Even the interned literal (the only case where == holds) leaves "--" as the target path. */
  public void testInternedDoubleDashLiteral() throws Exception {
    Options options = Options.parse(new String[] {"--", "target", "source1", "source2"});
    assertEquals("target", options.mergedIndexPath);
  }
}
