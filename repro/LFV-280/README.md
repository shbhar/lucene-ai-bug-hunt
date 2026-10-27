# PerFieldPostingsFormat merge leaves stale inherited format attributes on a field that has no postings left after a segment written by a non-per-field postings codec (for example SimpleTextCodec), so the merged segment cannot be opened

Bug LFV-280 · Area: codecs / per-field postings format merge (PerFieldPostingsFormat attribute bookkeeping) · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-280.html](../../bugs/LFV-280.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field with a per-field postings codec (for example a Lucene104Codec subclass that maps the field to its own postings format), then, in a writer session whose codec's postings format is not per-field (SimpleTextCodec or a custom codec), delete every document that has the field and force-merge, and then go back to the per-field codec and merge again. You expect the committed index to open, because every call returned normally. Instead the merged segment still records the field's old format name and suffix although the merge wrote no files for that group, so DirectoryReader.open and CheckIndex fail with CorruptIndexException caused by a missing &lt;segment&gt;_&lt;format&gt;_&lt;suffix&gt;.psm (for example _4_FST50_0.psm).

## Expected vs actual

- Expected: The committed index opens: 3 documents and no `tag:t` hit (2 documents in the default-codec test).
- Actual: 3 of 3 fail with `CorruptIndexException`: missing `_4_FST50_0.psm` (SimpleText and CheapBastard) and `_3_Lucene104_0.psm` (default codec only).

## Files

- [src/org/apache/lucene/codecs/perfield/TestPerFieldPostingsStaleAttributes.java](src/org/apache/lucene/codecs/perfield/TestPerFieldPostingsStaleAttributes.java) (JUnit test)
- [src2/org/apache/lucene/codecs/perfield/TestPerFieldPostingsStaleAttributes.java](src2/org/apache/lucene/codecs/perfield/TestPerFieldPostingsStaleAttributes.java) (JUnit test)
- [src/StaleAttributes7x.java](src/StaleAttributes7x.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldPostingsStaleAttributes
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.perfield.TestPerFieldPostingsStaleAttributes

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" StaleAttributes7x
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestPerFieldPostingsMergeStaleAttributes.java](TestPerFieldPostingsMergeStaleAttributes.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestPerFieldPostingsWrapperRoute.java](TestPerFieldPostingsWrapperRoute.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
