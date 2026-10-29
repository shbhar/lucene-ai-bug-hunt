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
package org.apache.lucene.spatial.composite;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.util.ShapeValuesPredicate;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

public class TestCompositeVerifyQueryVisit extends LuceneTestCase {

  public void testTermCollectorSeesIndexQuery() {
    SpatialContext ctx = SpatialContext.GEO;
    ShapeValuesPredicate predicate =
        new ShapeValuesPredicate(
            new SerializedDVStrategy(ctx, "shape").makeShapeValueSource(),
            SpatialOperation.Intersects,
            ctx.getShapeFactory().rect(-10, 10, -10, 10));
    Set<Term> terms = new HashSet<>();
    new CompositeVerifyQuery(new TermQuery(new Term("body", "a")), predicate)
        .visit(QueryVisitor.termCollector(terms));
    // expected the index query's term [body:a]; actual [] (visit only calls visitLeaf(this))
    assertEquals(Set.of(new Term("body", "a")), terms);
  }
}
