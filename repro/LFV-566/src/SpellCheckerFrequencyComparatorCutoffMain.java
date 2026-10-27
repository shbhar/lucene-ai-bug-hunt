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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.LevenshteinDistance;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.spell.SuggestWordFrequencyComparator;
import org.apache.lucene.store.ByteBuffersDirectory;

/** Version-portable (8.0+) main() form of TestSpellCheckerFrequencyComparatorCutoff. Exit 1 = bug. */
public class SpellCheckerFrequencyComparatorCutoffMain {
  public static void main(String[] args) throws Exception {
    ByteBuffersDirectory userDir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig(null))) {
      for (int i = 0; i < 50; i++) {
        Document d = new Document();
        d.add(new StringField("body", "receive", Field.Store.NO));
        w.addDocument(d);
      }
      for (String s :
          new String[] {
            "relieve", "retrieve", "reprieve", "revive", "recite", "receiver", "deceive", "review", "recede"
          }) {
        Document d = new Document();
        d.add(new StringField("body", s, Field.Store.NO));
        w.addDocument(d);
      }
    }
    boolean bug = false;
    try (IndexReader ir = DirectoryReader.open(userDir);
        SpellChecker sc =
            new SpellChecker(
                new ByteBuffersDirectory(),
                new LevenshteinDistance(),
                new SuggestWordFrequencyComparator())) {
      sc.indexDictionary(new LuceneDictionary(ir, "body"), new IndexWriterConfig(null), false);
      for (int numSug = 1; numSug <= 4; numSug++) {
        String[] s = sc.suggestSimilar("recieve", numSug, ir, "body", SuggestMode.SUGGEST_WHEN_NOT_IN_INDEX);
        System.out.println("numSug=" + numSug + " -> " + Arrays.toString(s));
        if (s.length == 0 || !s[0].equals("receive")) bug = true;
      }
    }
    System.out.println(bug ? "BUG: the most frequent word 'receive' is not ranked first" : "OK");
    System.exit(bug ? 1 : 0);
  }
}
