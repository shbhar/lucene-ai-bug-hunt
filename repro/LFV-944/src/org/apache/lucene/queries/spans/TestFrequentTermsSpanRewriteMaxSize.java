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
package org.apache.lucene.queries.spans;

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFrequentTermsSpanRewriteMaxSize extends LuceneTestCase {

  public void testMaxSizeIntegerMaxValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String value : new String[] {"aa1", "aa2", "aa3"}) {
          w.addDocument(List.of(new TextField("f", value, Field.Store.NO)));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SpanMultiTermQueryWrapper<PrefixQuery> query =
            new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term("f", "aa")));
        query.setRewriteMethod(new FrequentTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE));
        try {
          assertEquals(3, new IndexSearcher(reader).count(query));
        } catch (IllegalArgumentException e) {
          // Expected: the 3 matching terms are kept and count is 3; actual: the rewrite throws.
          throw new AssertionError("expected count 3, but the rewrite threw " + e, e);
        }
      }
    }
  }
}
