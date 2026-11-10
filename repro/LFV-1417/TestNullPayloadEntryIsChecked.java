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
package org.apache.lucene.analysis;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.util.BytesRef;

public class TestNullPayloadEntryIsChecked extends BaseTokenStreamTestCase {

  public void testUnexpectedPayloadIsReported() throws Exception {
    Token token = new Token("a", 0, 1);
    token.setPayload(new BytesRef(new byte[] {42}));
    // Expected: AssertionError (token has payload [42], null entry expects none); actual: the check passes.
    expectThrows(
        AssertionError.class,
        () ->
            assertTokenStreamContents(
                new CannedTokenStream(token),
                new String[] {"a"},
                null, null, null, null, null, null, null, null,
                true,
                new byte[][] {null}));
  }
}
