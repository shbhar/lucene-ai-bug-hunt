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
package org.apache.lucene;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.commongrams.TestCommonGramsFilter;
import org.apache.lucene.geo.TestXYRectangle;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.TestOperations;
import org.junit.runner.Description;
import org.junit.runner.Request;

public class TestMisnamedTestMethodsNeverRun extends LuceneTestCase {

  public void testEveryPublicNoArgMethodIsRunAsATest() {
    List<String> notRun = new ArrayList<>();
    for (Class<?> c :
        List.of(TestXYRectangle.class, TestOperations.class, TestCommonGramsFilter.class)) {
      List<String> run = new ArrayList<>();
      for (Description d : Request.aClass(c).getRunner().getDescription().getChildren()) {
        run.add(d.isSuite() ? d.getDisplayName() : d.getMethodName());
      }
      for (Method m : c.getDeclaredMethods()) {
        if (Modifier.isPublic(m.getModifiers())
            && Modifier.isStatic(m.getModifiers()) == false
            && m.getParameterCount() == 0
            && run.contains(m.getName()) == false) {
          notRun.add(c.getSimpleName() + "#" + m.getName());
        }
      }
    }
    notRun.sort(null);
    // expected: every public no-arg test method runs; actual: the four misnamed ones are skipped
    assertEquals(List.of(), notRun);
  }
}
