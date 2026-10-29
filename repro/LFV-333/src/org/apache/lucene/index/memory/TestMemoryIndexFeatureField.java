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
package org.apache.lucene.index.memory;

import org.apache.lucene.document.FeatureField;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMemoryIndexFeatureField extends LuceneTestCase {

  public void testLinearQueryScoresFeatureValue() {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new FeatureField("f", "pr", 10f), null);
    // expected: the feature value 10.0 (as with IndexWriter); actual: 4.5918E-41 (frequency 1)
    assertEquals(10f, mi.search(FeatureField.newLinearQuery("f", "pr", 1f)), 0f);
  }
}
