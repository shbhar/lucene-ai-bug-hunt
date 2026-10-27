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
package org.apache.lucene.search.grouping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

/**
 * GroupingSearch.setIgnoreDocsWithoutGroupField(true) is documented as "Whether to ignore
 * documents that don't have the group field instead of putting them in a null group". The
 * returned groups honour it, but setAllGroups(true) still counts the null group in
 * TopGroups.totalGroupCount and getAllMatchingGroups(), and setAllGroupHeads(true) still returns a
 * head document for it.
 *
 * <p>Index: doc 0 in group "a", doc 1 in group "b", doc 2 without the group field.
 */
public class TestIgnoreDocsWithoutGroupFieldAllGroups extends LuceneTestCase {

  public void testIgnoredDocsDoNotFormANullGroupAnywhere() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String g : new String[] {"a", "b", null}) {
          Document d = new Document();
          if (g != null) {
            d.add(new SortedDocValuesField("group", new BytesRef(g)));
          }
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        GroupingSearch gs = new GroupingSearch("group");
        gs.setIgnoreDocsWithoutGroupField(true).setAllGroups(true).setAllGroupHeads(true);
        TopGroups<BytesRef> tg = gs.search(searcher, new MatchAllDocsQuery(), 0, 10);

        // the returned groups honour the flag: a and b only
        assertEquals(2, tg.groups.length);
        for (GroupDocs<BytesRef> gd : tg.groups) {
          assertNotNull(gd.groupValue());
        }

        List<String> violations = new ArrayList<>();
        Collection<BytesRef> all = gs.getAllMatchingGroups();
        if (all.contains(null)) {
          violations.add("getAllMatchingGroups() contains the null group: " + all);
        }
        if (Integer.valueOf(2).equals(tg.totalGroupCount) == false) {
          violations.add("totalGroupCount=" + tg.totalGroupCount + " but 2 groups exist");
        }
        Bits heads = gs.getAllGroupHeads();
        if (heads.get(2)) {
          violations.add("doc 2 has no group field but is returned by getAllGroupHeads()");
        }
        assertTrue(String.join("; ", violations), violations.isEmpty());
      }
    }
  }
}
