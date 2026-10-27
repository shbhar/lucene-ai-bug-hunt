# FSTMetadata.save always writes the current version, so re-saving a pre-9.0 BYTE2 FST breaks its lookups

Bug LFV-052 · Area: util / FST · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-052.html](../../bugs/LFV-052.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load an FST that was written before 9.0 with BYTE2 labels and save it again with Lucene 9.0 or later. You expect the re-saved FST to work like the original. Instead save writes the header version VERSION_CURRENT, so readers decode the labels with the wrong byte order and the lookups fail (0 of 9 keys found in the repro); FSTs built with setVersion(VERSION_90) are also labelled 9 and rejected by 9.8.

## Expected vs actual

- Expected: The re-saved copy finds 9 of 9 keys, and a `VERSION_90` FST still reports version 8 after save and reload.
- Actual: The copy finds 0 of 9 keys (`getVersion` = 9, no exception), and the `VERSION_90` FST reports 9.

## Files

- [src/TestV90HeaderVerify.java](src/TestV90HeaderVerify.java) (JUnit test)
- [src/TestResaveOldByte2Fst.java](src/TestResaveOldByte2Fst.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- This repro reads private Lucene state by reflection (setAccessible), so it depends on internals that may change.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestV90HeaderVerify \
       --select-class TestResaveOldByte2Fst
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestFSTMetadataSaveVersion.java](TestFSTMetadataSaveVersion.java)
- [WriteV7Byte2Fst.java](WriteV7Byte2Fst.java)
- [ResavePre90Byte2Fst.java](ResavePre90Byte2Fst.java)
- [fix-save-own-version.diff](fix-save-own-version.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/study-fst-verification/](../../models/study-fst-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
