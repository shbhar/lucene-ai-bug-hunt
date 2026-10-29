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
package org.apache.lucene.facet.facetset;

import java.util.List;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestMatchingFacetSetsCountsSlicedValue extends LuceneTestCase {

  public void testBinaryValueWithOffset() throws Exception {
    BytesRef first = FacetSetsField.create("fs", new LongFacetSet(1, 2)).binaryValue();
    BytesRef second = FacetSetsField.create("fs", new LongFacetSet(3, 4)).binaryValue();
    byte[] buffer = new byte[first.length + second.length];
    System.arraycopy(first.bytes, first.offset, buffer, 0, first.length);
    System.arraycopy(second.bytes, second.offset, buffer, first.length, second.length);
    MemoryIndex index = new MemoryIndex();
    index.addField(
        new BinaryDocValuesField("fs", new BytesRef(buffer, first.length, second.length)), null);
    FacetsCollector hits =
        index.createSearcher().search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    MatchingFacetSetsCounts counts =
        new MatchingFacetSetsCounts(
            "fs",
            hits,
            FacetSetDecoder::decodeLongs,
            new ExactFacetSetMatcher("(3,4)", new LongFacetSet(3, 4)),
            new ExactFacetSetMatcher("(1,2)", new LongFacetSet(1, 2)));
    // the document's set is (3,4), in a slice after an encoding of (1,2): expected (3,4)=1, (1,2)=0
    assertEquals(
        List.of(new LabelAndValue("(3,4)", 1), new LabelAndValue("(1,2)", 0)),
        List.of(counts.getAllChildren("fs").labelValues));
  }
}
