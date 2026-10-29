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
package org.apache.lucene.analysis.synonym.word2vec;

import java.util.Collection;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

public class TestWord2VecSynonymProviderMaxSynonyms extends LuceneTestCase {

  public void testTiedVectorsMaxOneSynonym() throws Exception {
    Word2VecModel model = new Word2VecModel(3, 2);
    for (String term : new String[] {"a", "b", "c"}) {
      model.addTermAndVector(new TermAndVector(new BytesRef(term), new float[] {1f, 0f}));
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    Collection<TermAndBoost> synonyms = provider.getSynonyms(new BytesRef("c"), 1, 0.5f);
    assertTrue(
        "maxSynonymsPerTerm=1: expected at most 1 synonym of c, got "
            + synonyms.stream().map(s -> s.term().utf8ToString()).toList(),
        synonyms.size() <= 1);
  }
}
