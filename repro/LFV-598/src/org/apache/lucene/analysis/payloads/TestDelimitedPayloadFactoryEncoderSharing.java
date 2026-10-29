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
package org.apache.lucene.analysis.payloads;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ClasspathResourceLoader;

public class TestDelimitedPayloadFactoryEncoderSharing extends LuceneTestCase {

  public static final class ReusingFloatEncoder extends AbstractEncoder {
    private final BytesRef scratch = new BytesRef(new byte[4]);

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      float value = Float.parseFloat(new String(buffer, offset, length));
      PayloadHelper.encodeFloat(value, scratch.bytes, 0);
      return scratch;
    }
  }

  public void testFiltersFromOneFactoryKeepTheirOwnPayloads() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("encoder", ReusingFloatEncoder.class.getName());
    DelimitedPayloadTokenFilterFactory factory = new DelimitedPayloadTokenFilterFactory(args);
    factory.inform(new ClasspathResourceLoader(getClass()));
    TokenStream a = factory.create(whitespace("x|1.0"));
    TokenStream b = factory.create(whitespace("y|2.0"));
    PayloadAttribute payloadA = a.addAttribute(PayloadAttribute.class);
    a.reset();
    b.reset();
    assertTrue(a.incrementToken());
    assertTrue(b.incrementToken());
    BytesRef p = payloadA.getPayload();
    // expected: x|1.0 keeps payload 1.0; actual: 2.0, written by b through the shared encoder
    assertEquals("payload of x|1.0", 1.0f, PayloadHelper.decodeFloat(p.bytes, p.offset), 0f);
  }

  private static Tokenizer whitespace(String text) {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    return t;
  }
}
