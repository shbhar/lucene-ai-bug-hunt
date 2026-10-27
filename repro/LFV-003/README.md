# Bulk BinaryDocValues#binaryValues (new in 10.6) sizes its buffer in int: it fails at 2 GiB and can return wrong bytes at 4 GiB

Bug LFV-003 · Area: codecs / doc values · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-003.html](../../bugs/LFV-003.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call the new bulk BinaryDocValues#binaryValues on the default codec for documents whose values add up to 2^31 bytes or more, which CheckIndex at level 2 or 3 does. You expect the same values as the per-document loop. Instead it throws NegativeArraySizeException, and at 4 GiB or more the size can wrap so that, with assertions off, returned BytesRefs point at other documents' bytes; CheckIndex then reports a valid index as broken, and -exorcise removed every document.

## Expected vs actual

- Expected: Both values come back as the per-value loop returns them: first bytes 0 and 2.
- Actual: `NegativeArraySizeException: -2147483648`; on a 3 x 1 GiB index written by 10.5.2, the `branch_10_6` or main `CheckIndex -level 2` prints `docvalues ERROR [-2147483648]` and exits 1 (10.5.2's finds no problems).

## Files

- [src/TestBulkBinaryValuesIntOverflow.java](src/TestBulkBinaryValuesIntOverflow.java) (JUnit test)

## Run it

- Not run on Lucene main a9779ef3c193 (2026-10-26): the repro needs more memory than the rerun host had.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestBulkBinaryValuesIntOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [minimal-test.diff](minimal-test.diff)
- [MakeIndex.java](MakeIndex.java)
- [BulkCall.java](BulkCall.java)

Formal models and scripts this bug's part of the hunt used: [../../models/study-overflow-at-scale/](../../models/study-overflow-at-scale/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
