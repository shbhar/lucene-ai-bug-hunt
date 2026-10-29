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
package org.apache.lucene.analysis.opennlp;

import java.text.BreakIterator;
import org.apache.lucene.analysis.opennlp.tools.OpenNLPOpsFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

public class TestOpenNLPSentenceBreakIteratorLeadingSpace extends LuceneTestCase {

  public void testFollowingInLeadingSpace() throws Exception {
    OpenNLPOpsFactory.getSentenceModel("en-test-sent.bin", new ClasspathResourceLoader(getClass()));
    BreakIterator bi =
        new OpenNLPSentenceBreakIterator(OpenNLPOpsFactory.getSentenceDetector("en-test-sent.bin"));
    String text = "  Sentence number 1 has 6 words.";
    bi.setText(text);
    // expected: text.length() or DONE (as without the leading spaces); actual: StackOverflowError
    int next = bi.following(0);
    assertTrue("following(0) = " + next, next == text.length() || next == BreakIterator.DONE);
  }
}
