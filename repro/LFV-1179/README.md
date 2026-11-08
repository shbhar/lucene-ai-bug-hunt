# (Latent) PagedBytesDataInput.clone(), setPosition(getPosition()) and a skipBytes that lands on the end look up the block after the last one when the data ends exactly on a block border, so with 16 full blocks (for example 64 KiB at blockBits 12) they throw ArrayIndexOutOfBoundsException, and with other block counts they leave a null current block (since 3.5.0)

Finding LFV-1179 · Area: core / util: PagedBytes (PagedBytes.PagedBytesDataInput setPosition, clone, skipBytes; @lucene.internal) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1179.html](../../bugs/LFV-1179.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write data into a PagedBytes whose length is an exact multiple of its block size (here 16 blocks of 4 KiB), freeze it, read a PagedBytesDataInput from getDataInput() to the end, and then clone it, call setPosition(getPosition()), or skip the last bytes so that the position lands exactly on the end. You expect an input positioned at the end, as setPosition's javadoc ('Seek to a position previously obtained from getPosition') and DataInput.clone's ('positioned at the same point as the stream they were cloned from') promise, and as happens when the data ends inside a block or is empty. Instead setPosition looks up the block after the last one, because it resolves the block eagerly while the reads move to the next block only when they need more bytes, and throws ArrayIndexOutOfBoundsException 'Index 16 out of bounds for length 16' (or, for other block counts, leaves a null block that the next zero-length read trips over); no Lucene code makes these calls, so only code that uses the internal class directly is affected.

## Expected vs actual

- Expected: `in.clone()` returns an input at position 256 and the test passes.
- Actual: `ArrayIndexOutOfBoundsException: Index 16 out of bounds for length 16` at `PagedBytes$PagedBytesDataInput.setPosition(PagedBytes.java:323)` from `clone(PagedBytes.java:311)`.

## Files

- [src/org/apache/lucene/util/TestPagedBytesCloneAtEnd.java](src/org/apache/lucene/util/TestPagedBytesCloneAtEnd.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestPagedBytesCloneAtEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPagedBytesCloneAtEnd.java](TestPagedBytesCloneAtEnd.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
