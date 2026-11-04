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
package org.apache.lucene.search.join;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortFieldEqualsSymmetry extends LuceneTestCase {

  public void testEqualsIsSymmetric() {
    BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    BitSetProducer children = new QueryBitSetProducer(new TermQuery(new Term("type", "child")));
    SortField plain = new SortField("price", SortField.Type.INT);
    SortField join =
        new ToParentBlockJoinSortField("price", SortField.Type.INT, false, parents, children);
    // expected: plain.equals(join) == join.equals(plain); actual: true vs false
    assertEquals(plain.equals(join), join.equals(plain));
  }
}
