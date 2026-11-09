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
      "UEsDBBQAAAAIAAAAIQC0vJNalAAAACkBAAAGAAAAXzAuY2Zls7+eIy7uU5qcmpdqaeCcn1uQX5qX4ppXUpSZ"
          + "WswABEeWdd6Z3bnhglVHy5mcKYa3GNhZ9NJScg0YIGAulFaIh5nhkVdcHpaaXJJfVOyWX5SbWBJvoFeWmnwB"
          + "qrCGSA0VAYwQhcVQDSBrK05ABR2QBEs4mCCcACKNzo2AamhAmJKXewMquBoqeEBj8gsok2Fm0OtNAFBLAwQU"
          + "AAAACAAAACEAhjh3cVQBAACTAwAABgAAAF8wLmNmc7O/niMu4lOanJqXamngnJ9bkF+al+KSWJLIAARHlnXe"
          + "md254YJVR8uZnCmGt4BC9kD14jD1bpmpOSnFnnkpqRW+qWAtjBhaGhpYmYESXEDMBMQGDOjAwQHOhEmawUUU"
          + "HDEkYcoZGZkPaEx+AZOeL9DxEeo+WZj7PPKKy8NSk0vyi4rd8otyE0tweUwap454sLUN9hBLGuzPnjljC2Mj"
          + "Wy84QUaaAWq/HE7TwEFFqgMYQUEHCjtGJjAT2dr2isBvDDB7xbDEi2cKVvsYkA1ZcnvdNpB+GZj+YKD1qSkQ"
          + "U9wSi2HBhhm5DHzQuEAxz3ZCmD7+eICmFpLjAQRCJIIFQKHBDLaYiYGV7T8QINu/+80dM5D9Qijh4ZmXll+M"
          + "NSwYGctA4v+hgEk+ILUIrMU7Lw/FEXrFpWlpmRWMBrhVpIEpSZz+AGYCZKduzK/wQeb/Pr9lPwBQSwMEFAAA"
          + "AAgAAAAhAIGQ+DsEAQAAawEAAAUAAABfMC5zabO/niMu7FOanJqXamkQnJqem5pX4pmXls8ABEeWdd6Z3bnh"
          + "glVHy5mcKYa3GDgZEIARmcMMEuBiK84vLUpOZU3LKS3OYM8v1kssSs5gTwSRZiYiWYlliXpFpXklmbmpemWp"
          + "RcWZ+Xk8RqZ6BnrG2pa6PiHBXEAdUHH20LzsvPzyPKb8YlafzLzSCm6w7rLUvJT8IpgkJ8ik4pLE3AJeQ3NL"
          + "E0MLIxMDEOCBqgWbxAaxgR8ilovVYr4csP9hcqyWQCkDZrZ4A73ktFRWIFWcCeEUM8rDg6okvyg1xS0zNSel"
          + "2C2/KDexRC83PyWVy8k1OCQ+OMDV1YXhgMbkF7AAema2bgIAUEsDBBQAAAAIAAAAIQABcd2SXQAAAJoAAAAK"
          + "AAAAc2VnbWVudHNfMbO/niPOUZyanpuaV1LMwMDAdWRZ553ZnRsuWHW0nMmZYniX0ZCTgQGIwICFEUgwAnlM"
          + "8QZoCm9x+JQmp+alWhr8hwKQhv9oACTGiKbzDsTsAxqTX0CtYSh+5LEOAFBLAQIUAxQAAAAIAAAAIQC0vJNa"
          + "lAAAACkBAAAGAAAAAAAAAAAAAACAAQAAAABfMC5jZmVQSwECFAMUAAAACAAAACEAhjh3cVQBAACTAwAABgAA"
          + "AAAAAAAAAAAAgAG4AAAAXzAuY2ZzUEsBAhQDFAAAAAgAAAAhAIGQ+DsEAQAAawEAAAUAAAAAAAAAAAAAAIAB"
          + "MAIAAF8wLnNpUEsBAhQDFAAAAAgAAAAhAAFx3ZJdAAAAmgAAAAoAAAAAAAAAAAAAAIABVwMAAHNlZ21lbnRz"
          + "XzFQSwUGAAAAAAQABADTAAAA3AMAAAAA";

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
