# CodecUtil's codec header and footer javadoc gives Magic, Version, AlgorithmID and Checksum as the little-endian DataOutput#writeInt/#writeLong, but CodecUtil writes these fields big-endian, so a header or footer read or written as documented comes out byte-swapped or is rejected (documentation defect; the writer and reader agree)

Finding LFV-1439 · Area: codecs / CodecUtil (codec header and footer javadoc grammar vs the written bytes) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1439.html](../../bugs/LFV-1439.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a file with CodecUtil.writeHeader(out, "Foo", 1) and CodecUtil.writeFooter(out) and decode it as the CodecHeader and CodecFooter grammars in CodecUtil's javadoc say: they link Magic, Version and AlgorithmID to DataOutput#writeInt and Checksum to DataOutput#writeLong, which are little-endian since 9.0, so you read them with readInt/readLong and expect Magic 0x3fd76c17 (the javadoc says 'It is always' CODEC_MAGIC), Version 1, footer Magic 0xc02893e8 and the file's CRC. Instead you get Magic 0x176cd73f, Version 16777216, footer Magic 0xe89328c0 and Checksum 0x62233eeb00000000, each byte-swapped, because CodecUtil writes these fields big-endian (writeBEInt/writeBELong); the other way round, a header or footer written with writeInt/writeLong as documented is rejected by checkHeader/checkFooter with CorruptIndexException. Lucene's own reader matches its writer, so indexing and search are unaffected; only the five javadoc links, left unchanged when LUCENE-9047 made DataOutput little-endian in 9.0.0, are wrong.

## Expected vs actual

- Expected: Per the javadoc, `readInt()` returns `CODEC_MAGIC` (1071082519) and `FOOTER_MAGIC` (-1071082520).
- Actual: `AssertionError: expected:<[1071082519, -1071082520]> but was:<[393008959, -393008960]>`: both magic numbers byte-swapped.

## Files

- [src/org/apache/lucene/codecs/TestCodecHeaderFooterMagicAsDocumented.java](src/org/apache/lucene/codecs/TestCodecHeaderFooterMagicAsDocumented.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.TestCodecHeaderFooterMagicAsDocumented
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCodecHeaderFooterMagicAsDocumented.java](TestCodecHeaderFooterMagicAsDocumented.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
