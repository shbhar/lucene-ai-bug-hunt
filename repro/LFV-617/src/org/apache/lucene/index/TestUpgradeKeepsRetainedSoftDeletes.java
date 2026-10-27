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
package org.apache.lucene.index;

import java.io.InputStream;
import java.nio.file.Path;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Version;

/**
 * Upgrading an index with a SoftDeletesRetentionMergePolicy in the config must keep the
 * soft-deleted documents that the policy retains, like every other merge of that policy does.
 *
 * <p>Fixtures (written by an older Lucene release with MakeSoftDeletesIndex, soft-deletes field "soft"): 13
 * documents, 3 of them soft-deleted; softdeletes-1seg has one segment, softdeletes-2seg two.
 */
public class TestUpgradeKeepsRetainedSoftDeletes extends LuceneTestCase {

  /** Lucene release that wrote the fixtures (10.5.2; 9.12.3 for runs on 10.x releases). */
  private static final String FIXTURE_VERSION = System.getProperty("fixture.version", "10.5.2");

  /** The two 10.5.2 fixtures, embedded so that the test is self-contained (zip, base64). */
  private static final java.util.Map<String, String> EMBEDDED =
      java.util.Map.of(
          "softdeletes-1seg.10.5.2.zip",
      "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"
          + "DwAPAHQDAAAsCQAAAAA=",
          "softdeletes-2seg.10.5.2.zip",
      "UEsDBBQAAAAIAAAAWl17d7xeXwAAAJ0AAAAGAAAAXzAuZmRts7+eIy7uU5qcmpdqaeCWmZqTUuyZl5Ja4ZtaksjAwM"
          + "AYfNbLXv1Duc6rpqCH86SfPWNoaGBlBUpwATETEBswoIMFDnAmTNIMLlLghCFZBKUZGVkPaEx+AZM+6VArAQBQSwME"
          + "FAAAAAgAAABaXX5L4IxoAAAAggAAAAYAAABfMC5mZHSzv54jLuNTmpyal2ppEFySX5Sa4paZmpNS7JZYXOKSWJLIwM"
          + "DAGHzWy179Q7nOq6agh/Oknz1jYBBjYGJgY2RmYoEBZgEGA0YDDgNGQyBtCKWNoLQxlDbhUGA0PKAx+QUDFLD757sA"
          + "AFBLAwQUAAAACAAAAFpd2vA8+jwAAABAAAAABgAAAF8wLmZkeLO/niMu5lOanJqXamnglpmak1LsmZeSWuGZUsEABM"
          + "FnvezVP5TrvGoKejhP+tkzhgMak18wQIEm/88MAFBLAwQUAAAACAAAAFpdd8Q3GX4AAAD8AAAABgAAAF8wLmZubbO/"
          + "niMu5FOanJqXamnilpmak+KZl5ZfzMDAwBR81ste/UO5zqumoIfzpJ89Y2BiykxhYGJkYPgPBUyyAalFYE0B+cUlmX"
          + "npxW75RbmJJXppYIoTYq6hgQkudcWlaWmZFYwGDAyMDOxlqUXFmfl5jDSz4YDG5BcMUMDzYGccAFBLAwQUAAAACAAA"
          + "AFpdQaUMQiEBAACpAQAABQAAAF8wLnNps7+eIy7sU5qcmpdqaRCcmp6bmlfimZeWzwAEwWe97NU/lOu8agp6OE/62T"
          + "MGLqAgKxAzATEjMgdE///PwZ5frJdYlJzBnggizUy4gPyy1KLizPw89tC87Lz88jy+HLBdMGE2QwM9Uz0jtuL80qLk"
          + "VNa0nNLiDM6SzNzU4pLE3AJeQ3NLI0tzIwsDEBDJSixL1CsqzQPJwwzgMTLVM9Az1rbU9QkJ5garKEvNS8kvglnIlF"
          + "/M6pOZV1rBxRpvoFecyQYk01JyheIN4iHeNjQwAQqVZBZgCOWmoAsVFOdC9Fegy6TkJ2MaCVVcAqbychnl4QFdkl+U"
          + "muKWmZqTUuyWX5SbWKKXm5+SyuXkGhwSHxzg6urCcEBj8gsGKNijuC4eAFBLAwQUAAAACAAAAFpdG9nOhaMAAABdAQ"
          + "AACAAAAF8wXzEuZm5ts7+eIy7kU5qcmpdqaeKWmZqT4pmXll/MwMDAFHzWy179Q7nOq6agh/Oknz1jNGRmykxhYGJk"
          + "YPgPBUyyAalFYF0B+cUlmXnpxW75RbmJJXppYIoTYrChgQkudcWlaWmZFYwGDAyMDOxlqUXFmfl5jDSzgaU4P62EiQ"
          + "PIAtoABkxyMG0u+clhiTmlqajmc0CDxgCnOmTzD2hMfgE1mKF/WUUfAFBLAwQUAAAACAAAAFpdrFwD7U8AAABbAAAA"
          + "EwAAAF8wXzFfTHVjZW5lOTBfMC5kdmSzv54jLupTmpyal2pp4JKfHJaYU5pa7JJYksjAwMAUfNbLXv1Duc6rpqCH86"
          + "SfPeMxjIepjTcAygPVMALJ//UMDP//H9CY/IIBCuSZ5X0BUEsDBBQAAAAIAAAAWl3Bm8tGYgAAAKAAAAATAAAAXzBf"
          + "MV9MdWNlbmU5MF8wLmR2bbO/niMu6VOanJqXamngkp8clphTmlrsm1qSmJJYksjAwMAUfNbLXv1Duc6rpqCH86SfPe"
          + "MxjIepjzdgAqpgsGaAAAEGGOBkhjL+AwEDIwMq8Ebj/0cCBzQmv4CJf9P0bgQAUEsDBBQAAAAIAAAAWl0vpnsdSAAA"
          + "AFAAAAATAAAAXzBfMV9MdWNlbmU5MF8wLmR2c7O/niMu5VOanJqXamngkp8clphTmlocnJ1Z4JmXklrBwMDAFHzWy1"
          + "79Q7nOq6agh/Oknz3jMYyHaYg3OKAx+QUDFPxqEc8GAFBLAwQUAAAACAAAAFpdOcNMyEgAAABVAAAAEgAAAF8wX0x1"
          + "Y2VuZTEwNF8wLmRvY7O/niMu5VOanJqXamhgEpBfXJKZl14cXpRZklrkkp/MAATBZ73s1T+U67xqCno4T/rZM2648n"
          + "gDBkYQOKAx+QUDFOS6CvgAAFBLAwQUAAAACAAAAFpdoYLzlUoAAABoAAAAEgAAAF8wX0x1Y2VuZTEwNF8wLnBzbbO/"
          + "niMu7VOanJqXamhgEpBfXJKZl14cXpRZklrkm1qSyAAEwWe97NU/lOu8agp6OE/62TNuuPp4AwY0EAqlD2hMfgET27"
          + "0ozwkAUEsDBBQAAAAIAAAAWl1C+IPGXQAAAGIAAAASAAAAXzBfTHVjZW5lMTA0XzAudGlts7+eIy7klJOfnB1SlJoa"
          + "klqUW+ySmVzCAATBZ73s1T+U67xqCno4T/rZM26f0uTUvFRDA5N4A24dA0MjYxNuRkZOtjoGViBg5jFkZmTkYqw7oD"
          + "H5BQMUTLld9Q4AUEsDBBQAAAAIAAAAWl1d1ZkOTAAAAFwAAAASAAAAXzBfTHVjZW5lMTA0XzAudGlws7+eIy7slJOf"
          + "nB1SlJoaklqUW+yZl5JawQAEwWe97NU/lOu8agp6OE/62TNun9Lk1LxUQwOTeAMFcwYoUPCEsQ5oTH4BY7/69ucoAF"
          + "BLAwQUAAAACAAAAFpdsrFW2noAAACxAAAAEgAAAF8wX0x1Y2VuZTEwNF8wLnRtZLO/niMu5JSTn5wdUpSaGpJalFvs"
          + "m1qSyAAEwWe97NU/lOu8agp6OE/62TNun9Lk1LxUQwOTeAN7oDYZOD8gv7gkMy+9OLwosyS1CGwIIQMamJgYWFlZGQ"
          + "0YTSwYnBgZgUxDRkMnBp8YBghIgtIHNCa/gDIZpktaFAIAUEsDBBQAAAAIAAAAWl0db6XyXgAAAJ0AAAAGAAAAXzEu"
          + "ZmRts7+eIy7uU5qcmpdqaeCWmZqTUuyZl5Ja4ZtaksjAwMAYfNbLXv1Duc6rpqCH86SfvWJoaGDlAEpwATETEBswYA"
          + "BHOAsmaQYXmeOEIdkCpRkZOQ5oTH4Bk3bbd64TAFBLAwQUAAAACAAAAFpdwL7uM4MAAACUAAAABgAAAF8xLmZkdLO/"
          + "niMu41OanJqXamkQXJJflJrilpmak1Lsllhc4pJYksjAwMAYfNbLXv1Duc6rpqCH86SfvWJgUGJgYmBjYmVmgwEmBQ"
          + "bGAFMORkOGAEYzIBXAwGjOwRhgyMBowREAFGW0DABJMgYYcDAaAdUYAimgGiMORgGjAxqTXzBAwRQv8ccAUEsDBBQA"
          + "AAAIAAAAWl0Uf26gPAAAAEAAAAAGAAAAXzEuZmR4s7+eIy7mU5qcmpdqaeCWmZqTUuyZl5Ja4ZlSwQAEwWe97NU/lO"
          + "u8agp6OE/62SuGAxqTXzBAgbGx5DMAUEsDBBQAAAAIAAAAWl2MYnAlfgAAAPwAAAAGAAAAXzEuZm5ts7+eIy7kU5qc"
          + "mpdqaeKWmZqT4pmXll/MwMDAFHzWy179Q7nOq6agh/Okn71iYGLKTGFgYmRg+A8FTLIBqUVgTQH5xSWZeenFbvlFuY"
          + "klemlgihNirqGBCS51xaVpaZkVjAYMDIwM7GWpRcWZ+XmMNLPhgMbkFwxQsOCSuR0AUEsDBBQAAAAIAAAAWl2w9gj/"
          + "IgEAAKkBAAAFAAAAXzEuc2mzv54jLuxTmpyal2ppEJyanpuaV+KZl5bPAATBZ73s1T+U67xqCno4T/rZKwYuoCArED"
          + "MBMSMyhwOI///nYM8v1kssSs5gTwSRZiZcQH5ZalFxZn4ee2hedl5+eR5fDtgumDCboYGeqZ4RW3F+aVFyKmtaTmlx"
          + "BmdJZm5qcUlibgGvobmlkaW5kYUBCIhkJZYl6hWV5oHkYQbwGJnqGegZa1vq+oQEc4NVlKXmpeQXwSxkyi9m9cnMK6"
          + "3gEoo3jIf41NDAJN5ArySzAEMoN4U13lCvOBNdIiU/GVN7LhtQbVpKLrpMQTFUpgJM5UF5JYzy8IAuyS9KTXHLTM1J"
          + "KXbLL8pNLNHLzU9J5XJyDQ6JDw5wdXVhOKAx+QUDFISl+ywFAFBLAwQUAAAACAAAAFpdPbwzi0sAAABYAAAAEgAAAF"
          + "8xX0x1Y2VuZTEwNF8wLmRvY7O/niMu5VOanJqXamhgEpBfXJKZl14cXpRZklrkkp/MAATBZ73s1T+U67xqCno4T/rZ"
          + "K2648ngDBkYQYGNkPKAx+QUDFHg8uHUOAFBLAwQUAAAACAAAAFpde8DRc0oAAABoAAAAEgAAAF8xX0x1Y2VuZTEwNF"
          + "8wLnBzbbO/niMu7VOanJqXamhgEpBfXJKZl14cXpRZklrkm1qSyAAEwWe97NU/lOu8agp6OE/62StuuPp4AwY0EAGl"
          + "D2hMfgET8z16zR8AUEsDBBQAAAAIAAAAWl3piyJ1ZQAAAGsAAAASAAAAXzFfTHVjZW5lMTA0XzAudGlts7+eIy7klJ"
          + "OfnB1SlJoaklqUW+ySmVzCAATBZ73s1T+U67xqCno4T/rZK26f0uTUvFRDA5N4A0EXA0MjUzNzC0tBRkZ+zjpWVlZp"
          + "VhAQMTRiZWTiYmOq4zmgMfkFAxT8SrGVBQBQSwMEFAAAAAgAAABaXWixgCBMAAAAXAAAABIAAABfMV9MdWNlbmUxMD"
          + "RfMC50aXCzv54jLuyUk5+cHVKUmhqSWpRb7JmXklrBAATBZ73s1T+U67xqCno4T/rZK26f0uTUvFRDA5N4AwVzBihQ"
          + "8IexDmhMfgFjl7HNPA4AUEsDBBQAAAAIAAAAWl1slsKFegAAALEAAAASAAAAXzFfTHVjZW5lMTA0XzAudG1ks7+eIy"
          + "7klJOfnB1SlJoaklqUW+ybWpLIAATBZ73s1T+U67xqCno4T/rZK26f0uTUvFRDA5N4A3ugNhk4PyC/uCQzL704vCiz"
          + "JLUIbAghAxqYmBg4ODgYDRgtLRicGJmATENGIycGnxgGCMiG0gc0Jr+AMhmsDD9fBwBQSwMEFAAAAAgAAABaXWeDBY"
          + "iRAAAAOAEAAAoAAABzZWdtZW50c18ys7+eI85RnJqem5pXUszAwMAVfNbLXv1Duc6rpqCH86SfvWc04mJl4mKAAHYm"
          + "IMEEFGCKN0BT+IzTpzQ5NS/V0MDkPxQwIAAjEs3MiKb1HSNHvEG8oV5aXi5UCROzMEgkHmKkpUG8gV5KWS4WsWIsYi"
          + "lM8YZoNrzC7rj/aADsQjStbyAOP6Ax+QXMMxOcr+wAAFBLAQIUAxQAAAAIAAAAWl17d7xeXwAAAJ0AAAAGAAAAAAAA"
          + "AAAAAACkgQAAAABfMC5mZG1QSwECFAMUAAAACAAAAFpdfkvgjGgAAACCAAAABgAAAAAAAAAAAAAApIGDAAAAXzAuZm"
          + "R0UEsBAhQDFAAAAAgAAABaXdrwPPo8AAAAQAAAAAYAAAAAAAAAAAAAAKSBDwEAAF8wLmZkeFBLAQIUAxQAAAAIAAAA"
          + "Wl13xDcZfgAAAPwAAAAGAAAAAAAAAAAAAACkgW8BAABfMC5mbm1QSwECFAMUAAAACAAAAFpdQaUMQiEBAACpAQAABQ"
          + "AAAAAAAAAAAAAApIERAgAAXzAuc2lQSwECFAMUAAAACAAAAFpdG9nOhaMAAABdAQAACAAAAAAAAAAAAAAApIFVAwAA"
          + "XzBfMS5mbm1QSwECFAMUAAAACAAAAFpdrFwD7U8AAABbAAAAEwAAAAAAAAAAAAAApIEeBAAAXzBfMV9MdWNlbmU5MF"
          + "8wLmR2ZFBLAQIUAxQAAAAIAAAAWl3Bm8tGYgAAAKAAAAATAAAAAAAAAAAAAACkgZ4EAABfMF8xX0x1Y2VuZTkwXzAu"
          + "ZHZtUEsBAhQDFAAAAAgAAABaXS+mex1IAAAAUAAAABMAAAAAAAAAAAAAAKSBMQUAAF8wXzFfTHVjZW5lOTBfMC5kdn"
          + "NQSwECFAMUAAAACAAAAFpdOcNMyEgAAABVAAAAEgAAAAAAAAAAAAAApIGqBQAAXzBfTHVjZW5lMTA0XzAuZG9jUEsB"
          + "AhQDFAAAAAgAAABaXaGC85VKAAAAaAAAABIAAAAAAAAAAAAAAKSBIgYAAF8wX0x1Y2VuZTEwNF8wLnBzbVBLAQIUAx"
          + "QAAAAIAAAAWl1C+IPGXQAAAGIAAAASAAAAAAAAAAAAAACkgZwGAABfMF9MdWNlbmUxMDRfMC50aW1QSwECFAMUAAAA"
          + "CAAAAFpdXdWZDkwAAABcAAAAEgAAAAAAAAAAAAAApIEpBwAAXzBfTHVjZW5lMTA0XzAudGlwUEsBAhQDFAAAAAgAAA"
          + "BaXbKxVtp6AAAAsQAAABIAAAAAAAAAAAAAAKSBpQcAAF8wX0x1Y2VuZTEwNF8wLnRtZFBLAQIUAxQAAAAIAAAAWl0d"
          + "b6XyXgAAAJ0AAAAGAAAAAAAAAAAAAACkgU8IAABfMS5mZG1QSwECFAMUAAAACAAAAFpdwL7uM4MAAACUAAAABgAAAA"
          + "AAAAAAAAAApIHRCAAAXzEuZmR0UEsBAhQDFAAAAAgAAABaXRR/bqA8AAAAQAAAAAYAAAAAAAAAAAAAAKSBeAkAAF8x"
          + "LmZkeFBLAQIUAxQAAAAIAAAAWl2MYnAlfgAAAPwAAAAGAAAAAAAAAAAAAACkgdgJAABfMS5mbm1QSwECFAMUAAAACA"
          + "AAAFpdsPYI/yIBAACpAQAABQAAAAAAAAAAAAAApIF6CgAAXzEuc2lQSwECFAMUAAAACAAAAFpdPbwzi0sAAABYAAAA"
          + "EgAAAAAAAAAAAAAApIG/CwAAXzFfTHVjZW5lMTA0XzAuZG9jUEsBAhQDFAAAAAgAAABaXXvA0XNKAAAAaAAAABIAAA"
          + "AAAAAAAAAAAKSBOgwAAF8xX0x1Y2VuZTEwNF8wLnBzbVBLAQIUAxQAAAAIAAAAWl3piyJ1ZQAAAGsAAAASAAAAAAAA"
          + "AAAAAACkgbQMAABfMV9MdWNlbmUxMDRfMC50aW1QSwECFAMUAAAACAAAAFpdaLGAIEwAAABcAAAAEgAAAAAAAAAAAA"
          + "AApIFJDQAAXzFfTHVjZW5lMTA0XzAudGlwUEsBAhQDFAAAAAgAAABaXWyWwoV6AAAAsQAAABIAAAAAAAAAAAAAAKSB"
          + "xQ0AAF8xX0x1Y2VuZTEwNF8wLnRtZFBLAQIUAxQAAAAIAAAAWl1ngwWIkQAAADgBAAAKAAAAAAAAAAAAAACkgW8OAA"
          + "BzZWdtZW50c18yUEsFBgAAAAAZABkAtwUAACgPAAAAAA==");

  private Directory open(String name) throws Exception {
    Path path = createTempDir(name);
    InputStream in = getClass().getResourceAsStream(name);
    if (in == null && EMBEDDED.containsKey(name)) {
      in = new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(EMBEDDED.get(name)));
    }
    assumeTrue("fixture " + name + " not on the classpath", in != null);
    try (InputStream is = in) {
      TestUtil.unzip(is, path);
    }
    return newFSDirectory(path);
  }

  /** Retains every soft-deleted document (retention query: match all). */
  private static IndexWriterConfig retainAllConfig() {
    return new IndexWriterConfig(null)
        .setSoftDeletesField("soft")
        .setMergePolicy(
            new SoftDeletesRetentionMergePolicy(
                "soft", MatchAllDocsQuery::new, new TieredMergePolicy()));
  }

  private static void assertUpgradedAndRetained(Directory dir) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        assertEquals(
            Version.LATEST, ((SegmentReader) ctx.reader()).getSegmentInfo().info.getVersion());
      }
      // 13 documents in total: 10 visible, 3 soft-deleted that the retention policy keeps
      assertEquals("retained soft-deleted documents were dropped", 13, reader.maxDoc());
    }
    try (DirectoryReader visible =
        new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft")) {
      assertEquals(10, visible.numDocs());
    }
  }

  /** Control: the delegate merges the two old segments, the retention policy wraps that merge. */
  public void testIndexUpgraderTwoSegments() throws Exception {
    try (Directory dir = open("softdeletes-2seg." + FIXTURE_VERSION + ".zip")) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(2, reader.leaves().size());
        assertEquals(13, reader.maxDoc());
      }
      new IndexUpgrader(dir, retainAllConfig(), false).upgrade();
      assertUpgradedAndRetained(dir);
    }
  }

  /**
   * The delegate (TieredMergePolicy) sees a single segment as already merged, so the segment is
   * rewritten by UpgradeIndexMergePolicy's own "merge remaining ones into new segment" OneMerge,
   * which the retention policy never wraps.
   */
  public void testIndexUpgraderOneSegment() throws Exception {
    try (Directory dir = open("softdeletes-1seg." + FIXTURE_VERSION + ".zip")) {
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        assertEquals(13, reader.maxDoc());
      }
      new IndexUpgrader(dir, retainAllConfig(), false).upgrade();
      assertUpgradedAndRetained(dir);
    }
  }
}
