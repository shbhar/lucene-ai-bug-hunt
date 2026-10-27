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
package org.apache.lucene.classification.document;

import java.util.List;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Independent check on the upstream fixture (the same index and ambiguous Batman document that
 * upstream testBoostedDocumentClassification uses). Each test method prints what it observed, and
 * the assertions encode the documented behaviour: the "^100" boost applies on every call.
 */
public class TestSnbDocBoostV2 extends DocumentClassificationTestBase<BytesRef> {

  private SimpleNaiveBayesDocumentClassifier boosted(String[] names) {
    return new SimpleNaiveBayesDocumentClassifier(
        indexReader, null, categoryFieldName, field2analyzer, names);
  }

  private static String dump(List<ClassificationResult<BytesRef>> l) {
    StringBuilder sb = new StringBuilder();
    for (ClassificationResult<BytesRef> r : l) {
      sb.append(r.assignedClass().utf8ToString()).append('=').append(r.score()).append(' ');
    }
    return sb.toString();
  }

  /** Upstream fixture, the boosted classifier, assignClass twice on the same document. */
  public void testUpstreamFixtureAssignTwice() throws Exception {
    try {
      String[] names = {textFieldName, titleFieldName + "^100", authorFieldName};
      SimpleNaiveBayesDocumentClassifier c = boosted(names);
      ClassificationResult<BytesRef> r1 = c.assignClass(getBatmanAmbiguousDocument());
      ClassificationResult<BytesRef> r2 = c.assignClass(getBatmanAmbiguousDocument());
      System.out.println(
          "V2 upstream fixture: call1="
              + r1.assignedClass().utf8ToString()
              + " ("
              + r1.score()
              + ") call2="
              + r2.assignedClass().utf8ToString()
              + " ("
              + r2.score()
              + ") names-after="
              + String.join(",", names));
      assertEquals("call 1 (matches upstream assertion)", BATMAN_RESULT, r1.assignedClass());
      assertEquals("call 2 on the same classifier", BATMAN_RESULT, r2.assignedClass());
    } finally {
      IOUtils.close(indexReader);
    }
  }

  /**
   * Control: the second call of the boosted classifier is score-identical to the unboosted
   * classifier, and a fresh boosted classifier reproduces call 1. Shows the boost is what is lost.
   */
  public void testSecondCallEqualsUnboosted() throws Exception {
    try {
      SimpleNaiveBayesDocumentClassifier b =
          boosted(new String[] {textFieldName, titleFieldName + "^100", authorFieldName});
      List<ClassificationResult<BytesRef>> first = b.getClasses(getBatmanAmbiguousDocument());
      List<ClassificationResult<BytesRef>> second = b.getClasses(getBatmanAmbiguousDocument());
      List<ClassificationResult<BytesRef>> fresh =
          boosted(new String[] {textFieldName, titleFieldName + "^100", authorFieldName})
              .getClasses(getBatmanAmbiguousDocument());
      List<ClassificationResult<BytesRef>> unboosted =
          boosted(new String[] {textFieldName, titleFieldName, authorFieldName})
              .getClasses(getBatmanAmbiguousDocument());
      System.out.println("V2 boosted call1:  " + dump(first));
      System.out.println("V2 fresh boosted:  " + dump(fresh));
      System.out.println("V2 boosted call2:  " + dump(second));
      System.out.println("V2 unboosted:      " + dump(unboosted));
      // facts (not the contract): call1 == fresh, call2 == unboosted
      assertEquals("call1 vs fresh boosted (determinism control)", dump(fresh), dump(first));
      boolean call2EqualsUnboosted = dump(second).equals(dump(unboosted));
      System.out.println("V2 call2 score-identical to unboosted: " + call2EqualsUnboosted);
      // contract: same classifier, same document, same result
      assertEquals("call2 must equal call1 (boost kept)", dump(first), dump(second));
    } finally {
      IOUtils.close(indexReader);
    }
  }

  /** Control: the KNN sibling with the same documented syntax does not rewrite the array. */
  public void testKnnSiblingKeepsArray() throws Exception {
    try {
      String[] names = {textFieldName, titleFieldName + "^100", authorFieldName};
      KNearestNeighborDocumentClassifier knn =
          new KNearestNeighborDocumentClassifier(
              indexReader, null, null, 1, 1, 1, categoryFieldName, field2analyzer, names);
      ClassificationResult<BytesRef> a = knn.assignClass(getBatmanAmbiguousDocument());
      ClassificationResult<BytesRef> b = knn.assignClass(getBatmanAmbiguousDocument());
      System.out.println(
          "V2 knn: call1="
              + a.assignedClass().utf8ToString()
              + " call2="
              + b.assignedClass().utf8ToString()
              + " names-after="
              + String.join(",", names));
      assertEquals(titleFieldName + "^100", names[1]);
      assertEquals(a.assignedClass(), b.assignedClass());
    } finally {
      IOUtils.close(indexReader);
    }
  }
}
