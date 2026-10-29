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
package org.apache.lucene.search;

import java.util.List;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automaton;

public class TestAutomatonQueryNfaDeadState extends LuceneTestCase {

  public void testDocValuesRewriteOfNfaWithReachableDeadState() throws Exception {
    Automaton nfa = new Automaton();
    int s0 = nfa.createState();
    int dead = nfa.createState();
    int accept = nfa.createState();
    int s3 = nfa.createState();
    nfa.setAccept(accept, true);
    nfa.addTransition(s0, dead, 'a');
    nfa.addTransition(s0, accept, 'b');
    nfa.addTransition(s0, s3, 'b');
    nfa.addTransition(s3, accept, 'c');
    nfa.finishState();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(List.of(new SortedSetDocValuesField("f", new BytesRef("b"))));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q = new AutomatonQuery(new Term("f"), nfa, false, MultiTermQuery.DOC_VALUES_REWRITE);
        int count;
        try {
          count = new IndexSearcher(reader).count(q);
        } catch (ArrayIndexOutOfBoundsException e) {
          // Expected: count 1 (the doc with "b"); actual: AutomatonTermsEnum throws AIOOBE.
          throw new AssertionError("expected count 1, got " + e, e);
        }
        assertEquals(1, count);
      }
    }
  }
}
