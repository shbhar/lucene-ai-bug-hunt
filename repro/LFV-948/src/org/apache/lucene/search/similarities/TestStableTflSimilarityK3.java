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
package org.apache.lucene.search.similarities;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStableTflSimilarityK3 extends LuceneTestCase {

  public void testK3ConstructorAdvertisedByChanges() {
    Constructor<?>[] ctors = StableTflSimilarity.class.getConstructors();
    boolean hasK3 = Arrays.stream(ctors).anyMatch(c -> c.getParameterCount() == 3);
    // 10.6.0 CHANGES (GITHUB#16243) "also supports k3": expected a (k1, c, k3) constructor
    assertTrue("expected StableTflSimilarity(k1, c, k3); actual: " + Arrays.toString(ctors), hasK3);
  }
}
