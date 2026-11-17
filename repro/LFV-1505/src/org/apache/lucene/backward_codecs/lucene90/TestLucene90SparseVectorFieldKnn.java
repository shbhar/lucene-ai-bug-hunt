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
package org.apache.lucene.backward_codecs.lucene90;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene90SparseVectorFieldKnn extends LuceneTestCase {

  private static final String INDEX_WRITTEN_BY_LUCENE_9_0_0_ZIP =
      "UEsDBBQAAAAIAAAAh1P4N6eilAAAACkBAAAGAAAAXzAuY2Zls7+eIy7uU5qcmpdqaeCcn1uQX5qX4ppXUpSZWswA" +
      "BDuVVq0yaSjZ+vGczeHi7v/ODOwsemkpuQYMEDAXSivEw8zwyCsuD0tNLskvKnbLL8pNLIk30CtLTb4AVZhDpIYK" +
      "B0aIwnSoBpC1FSuggg5IgiUvoIIBRBqda8EEUViDMCUvdwdUcDVU8IDG5BdQJsNL21vNAFBLAwQUAAAACAAAAIdT" +
      "t8aBeDgBAABzAwAABgAAAF8wLmNmc7O/niMu4lOanJqXamngnJ9bkF+al+KSWJLIAAQ7lVatMmko2frxnM3h4u7/" +
      "zkAhe6B6cZh6t8zUnJRiz7yU1ArfVLAWRgwtDQ2sTEAJLiAG0QYMGMABzoJJmsFFFBwxJGHKGRmZDmhMfgGTjpl3" +
      "4gPUfbIw93nkFZeHpSaX5BcVu+UX5SaW4PKYNE4d8WBrG+xBJLJ1ifO0Ixmg9snh1A0OGnIsRLbqdkREI9geMSzh" +
      "7pmC1XwUA8oZOINA+mVg+oOB1qWmQExxSyyGBQtm5IGjDSSDYp4hh0MM/nCGpgaSvQ0CIRzBLExgO0H4PxAg2734" +
      "mdlpWLgLoYSHZ15afjHWsGBkLAOJ/4cCJvmA1CKwFu+8PBRH6BWXpqVlVjAa4FaRBqYkcfoDmMiRncvxaedmZP6n" +
      "6O9LAFBLAwQUAAAACAAAAIdTxAs6nQQBAABrAQAABQAAAF8wLnNps7+eIy7sU5qcmpdqaRCcmp6bmlfimZeWzwAE" +
      "O5VWrTJpKNn68ZzN4eLu/84MnAwIwIjMYQIJcPFnJZYl6pXl6pWlFhVn5ufxGJnqGegZa1vq+oQE8+WALYHJsVoC" +
      "pQzYivNLi5JTWdNySosz2POL9RKLkjPYE0GkmYkI2Lii0rySzNxUrGZyAXVAxdlD87Lz8svzmPKLWX0y80oruCGO" +
      "Sc1LyS+CSXKCTCouScwt4DU0tzQxNzCwMAABHqhasElsEBuY2eIN9JLTUlmBVHEmhFPMKA8PqpL8otQUt8zUnJRi" +
      "t/yi3MQSvdz8lFQuJ9fgkPjgAFdXF4YDGpNfwAJob+NkMQBQSwMEFAAAAAgAAACHU5P+4cNdAAAAmgAAAAoAAABz" +
      "ZWdtZW50c18xs7+eI85RnJqem5pXUszAwMC1U2nVKpOGkq0fz9kcLu7+78ZoyMnAAERgwMIIJBiBPKZ4AzSFzhw+" +
      "pcmpeamWBv+hAKThPxoAiTGi6XSFmH1AY/ILqDUMt0u3RAMAUEsBAhQDFAAAAAgAAACHU/g3p6KUAAAAKQEAAAYA" +
      "AAAAAAAAAAAAAKQBAAAAAF8wLmNmZVBLAQIUAxQAAAAIAAAAh1O3xoF4OAEAAHMDAAAGAAAAAAAAAAAAAACkAbgA" +
      "AABfMC5jZnNQSwECFAMUAAAACAAAAIdTxAs6nQQBAABrAQAABQAAAAAAAAAAAAAApAEUAgAAXzAuc2lQSwECFAMU" +
      "AAAACAAAAIdTk/7hw10AAACaAAAACgAAAAAAAAAAAAAApAE7AwAAc2VnbWVudHNfMVBLBQYAAAAABAAEANMAAADA" +
      "AwAAAAA=";

  public void testKnnHitIsTheDocumentThatHasTheVector() throws Exception {
    Path path = createTempDir();
    byte[] zip = Base64.getDecoder().decode(INDEX_WRITTEN_BY_LUCENE_9_0_0_ZIP);
    TestUtil.unzip(new ByteArrayInputStream(zip), path);
    try (Directory dir = FSDirectory.open(path);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      TopDocs hits = searcher.search(new KnnFloatVectorQuery("v", new float[] {1, 0}, 1), 1);
      // doc 0 has no vector, doc 1 has [1, 0]: expected doc 1; actual on 9.8.0 to 10.6.0: doc 0
      assertEquals(1, hits.scoreDocs[0].doc);
    }
  }
}
