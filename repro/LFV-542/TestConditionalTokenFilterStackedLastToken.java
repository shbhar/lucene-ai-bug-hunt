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

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;

/**
 * A ConditionalTokenFilter must not add a position at the end of the stream when the wrapped
 * filters remove a stacked last token (position increment 0).
 */
public class TestConditionalTokenFilterStackedLastToken extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP_A = new CharArraySet(List.of("a"), false);

  /** "x", then "a" stacked on it (increment 0), then the given final position increment. */
  private static TokenStream xa(int finalPosInc) {
    return new CannedTokenStream(finalPosInc, 1, new Token("x", 1, 0, 1), new Token("a", 0, 0, 1));
  }

  /** A condition that is true only for the token "a". */
  private static TokenStream onlyA(TokenStream in, Function<TokenStream, TokenStream> wrapped) {
    return new ConditionalTokenFilter(in, wrapped) {
      private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

      @Override
      protected boolean shouldFilter() {
        return termAtt.toString().equals("a");
      }
    };
  }

  private static void assertEnd(TokenStream ts, int finalPosInc) throws IOException {
    assertTokenStreamContents(
        ts,
        new String[] {"x"},
        null,
        null,
        null,
        new int[] {1},
        null,
        1,
        finalPosInc,
        null,
        true,
        null);
  }

  /** The wrapped filters remove a stacked last token: the stream ends without a hole. */
  public void testStackedLastTokenRemoved() throws IOException {
    assertEnd(new StopFilter(xa(0), STOP_A), 0); // control: the plain chain
    assertEnd(onlyA(xa(0), in -> new StopFilter(in, STOP_A)), 0);
    CharArraySet protectX = new CharArraySet(List.of("x"), false);
    assertEnd(new ProtectedTermFilter(protectX, xa(0), in -> new StopFilter(in, STOP_A)), 0);
  }

  /** The same with a trailing hole from the input: exactly that hole is reported. */
  public void testStackedLastTokenRemovedBeforeTrailingHole() throws IOException {
    assertEnd(new StopFilter(xa(1), STOP_A), 1); // control: the plain chain
    assertEnd(onlyA(xa(1), in -> new StopFilter(in, STOP_A)), 1);
  }
}
