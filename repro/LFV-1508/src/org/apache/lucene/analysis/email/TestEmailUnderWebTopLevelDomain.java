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
package org.apache.lucene.analysis.email;

import java.io.StringReader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestEmailUnderWebTopLevelDomain extends BaseTokenStreamTestCase {
  public void testEmailUnderWebIsOneEmailToken() throws Exception {
    Tokenizer tokenizer = new UAX29URLEmailTokenizer();
    tokenizer.setReader(new StringReader("user@example.web"));
    // expected (as on 10.6.0): one <EMAIL> token; actual: user and example.web, both <ALPHANUM>
    assertTokenStreamContents(
        tokenizer, new String[] {"user@example.web"}, new String[] {"<EMAIL>"});
  }
}
