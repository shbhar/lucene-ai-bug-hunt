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
import org.apache.lucene.search.FloatVectorSimilarityQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene90VectorSimilarityQuery extends LuceneTestCase {

  private static final String INDEX_ZIP_BASE64 =
      "UEsDBBQAAAAIAAAAIQDzq6iJlAAAACkBAAAGAAAAXzAuY2Zls7+eIy7uU5qcmpdqaeCcn1uQX5qX4ppXUpSZWswA" +
      "BE7ah453BEZN+170emni7B/zGdhZ9NJScg0YIGAulFaIh5nhkVdcHpaaXJJfVOyWX5SbWBJvoFeWmnwBqrCGSA0V" +
      "AYwQhcVQDSBrK05ABR2QBEs4mCCcACKNzo2AamhAmJKXewMquBoqeEBj8gsok+H/1AYbAFBLAwQUAAAACAAAACEA" +
      "2xlS5lQBAACTAwAABgAAAF8wLmNmc7O/niMu4lOanJqXamngnJ9bkF+al+KSWJLIAARO2oeOdwRGTfte9Hpp4uwf" +
      "84FC9kD14jD1bpmpOSnFnnkpqRW+qWAtjBhaGhpYmYESXEDMBMQGDOjAwQHOhEmawUUUHDEkYcoZGZkPaEx+AZNW" +
      "+yWcCHWfLMx9HnnF5WGpySX5RcVu+UW5iSW4PCaNU0c82NoGe4glDfZnz5yxhbGRre9VPBXNALVfDqdp4KAi1QGM" +
      "oKADhR0jE5iJbO3pKY0ZDDB7xbDEi2cKVvsYkA1xvXd0D0i/DEx/MND61BSIKW6JxbBgw4xcBj5oXKCYJ7m5KgJ/" +
      "PEBTC8nxAAIhEsECoNBgBlvMxMDK9h8IkO1fO7tXHmS/EEp4eOal5RdjDQtGxjKQ+H8oYJIPSC0Ca/HOy0NxhF5x" +
      "aVpaZgWjAW4VaWBKEqc/gJkA2annfmyfjsy3OG/iCwBQSwMEFAAAAAgAAAAhAGdeUNQCAQAAawEAAAUAAABfMC5z" +
      "abO/niMu7FOanJqXamkQnJqem5pX4pmXls8ABE7ah453BEZN+170emni7B/zGTgZEIARmcMMEuDiLMnMTS0uScwt" +
      "4DU0tzQxtDAyMQAB7qzEskS9stS8lPwi9tC87Lz88jym/GJWn8y80gqu/GKgVFFxZn4eTE4ErL6oNA9kHkySx8hU" +
      "z0DPWNtS1yckmB2oKbEoOYM9EUSambAV55cWJaeypuWUFmfw5YC9A9PIagnUZ8APcUMuVuN4oA4Ey7BBZJjZ4g30" +
      "ktNSWYFUcSaEU8woDw+qkvyi1BS3zNSclGK3/KLcxBK93PyUVC4n1+CQ+OAAV1cXhgMak1/AAuj97H9+AFBLAwQU" +
      "AAAACAAAACEA5o7/1l0AAACaAAAACgAAAHNlZ21lbnRzXzGzv54jzlGcmp6bmldSzMDAwOWkfeh4R2DUtO9Fr5cm" +
      "zv6xiNGQk4EBiMCAhRFIMAJ5TPEGaArnc/iUJqfmpVoa/IcCkIb/aAAkxoimcyHE7AMak19ArWH4+T3gEgBQSwEC" +
      "FAMUAAAACAAAACEA86uoiZQAAAApAQAABgAAAAAAAAAAAAAAgAEAAAAAXzAuY2ZlUEsBAhQDFAAAAAgAAAAhANsZ" +
      "UuZUAQAAkwMAAAYAAAAAAAAAAAAAAIABuAAAAF8wLmNmc1BLAQIUAxQAAAAIAAAAIQBnXlDUAgEAAGsBAAAFAAAA" +
      "AAAAAAAAAACAATACAABfMC5zaVBLAQIUAxQAAAAIAAAAIQDmjv/WXQAAAJoAAAAKAAAAAAAAAAAAAACAAVUDAABz" +
      "ZWdtZW50c18xUEsFBgAAAAAEAAQA0wAAANoDAAAAAA==";

  public void testAllDocsAboveThreshold() throws Exception {
    Path path = createTempDir();
    TestUtil.unzip(new ByteArrayInputStream(Base64.getDecoder().decode(INDEX_ZIP_BASE64)), path);
    try (Directory dir = FSDirectory.open(path);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      FloatVectorSimilarityQuery query =
          new FloatVectorSimilarityQuery.Adaptive("v", new float[] {1, 0}, 0.5f);
      // 9.0.0-written docs [1,0], [1,0.1], [0,1]: expected 2 hits (similarity 1.0, 0.99), actual 1
      assertEquals(2, searcher.count(query));
    }
  }
}
