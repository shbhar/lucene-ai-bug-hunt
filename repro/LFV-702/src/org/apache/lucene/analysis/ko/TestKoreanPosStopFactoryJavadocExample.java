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
package org.apache.lucene.analysis.ko;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The class javadoc of KoreanPartOfSpeechStopFilterFactory shows the configuration
 * {@code <filter class="solr.KoreanPartOfSpeechStopFilterFactory" tags="E,J"/>}. The tags "E"
 * (verbal endings) and "J" (particles) were replaced by fine-grained tags (EP, EF, EC, ETN, ETM,
 * JKS, ..., JC) in 9.4, and POS.resolveTag no longer maps them, so the documented configuration
 * fails to load.
 */
public class TestKoreanPosStopFactoryJavadocExample extends BaseTokenStreamTestCase {

  public void testJavadocExampleTags() throws Exception {
    TokenStream ts = new KoreanTokenizer();
    ((Tokenizer) ts).setReader(new StringReader(" 한국은 대단한 나라입니다."));
    Map<String, String> args = new HashMap<>();
    args.put("tags", "E,J"); // verbatim from the factory's class javadoc
    KoreanPartOfSpeechStopFilterFactory factory = new KoreanPartOfSpeechStopFilterFactory(args);
    ts = factory.create(ts);
    // particles (은) and endings (ㄴ, ㅂ니다) removed, as with the fine-grained tags in the
    // upstream factory test
    assertTokenStreamContents(ts, new String[] {"한국", "대단", "하", "나라", "이"});
  }
}
