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
package org.apache.lucene.analysis.miscellaneous;

import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.tests.analysis.BaseTokenStreamFactoryTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.Version;

public class TestProtectedTermWrappedFiltersMatchVersion extends BaseTokenStreamFactoryTestCase {

  public void testWrappedTruncateUsesFactoryMatchVersion() throws Exception {
    TokenFilterFactory factory =
        tokenFilterFactory(
            "protectedTerm",
            Version.LUCENE_10_4_0,
            new StringMockResourceLoader("protectedword"),
            "protected", "protected.txt",
            "wrappedFilters", "truncate",
            "truncate.prefixLength", "4");
    // expected (10.4.0 prefixLength = 4 UTF-16 chars): "ab" + 1 emoji; actual (LATEST = 4 code points): "ab" + 2 emoji
    assertTokenStreamContents(
        factory.create(whitespaceMockTokenizer("ab😀😀cd")),
        new String[] {"ab😀"});
  }
}
