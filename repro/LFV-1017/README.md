# On main (11.0), the MIGRATE.md recipe for compound-file settings (iwc.getCodec.compoundFormat.setShouldUseCompoundFile / setMaxCFSSegmentSizeMB) changes the CompoundFormat of the single shared default codec, so one writer's settings silently become the compound-file settings of every other default-codec IndexWriter in the JVM

Bug LFV-1017 · Area: codecs / compound-file settings (CompoundFormat on the shared default codec; MIGRATE.md 11.0 recipe) · Priority: unranked · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You follow the 11.0 MIGRATE.md recipe on one writer's fresh IndexWriterConfig (CompoundFormat cfs = iwc.getCodec.compoundFormat; cfs.setShouldUseCompoundFile(false); cfs.setMaxCFSSegmentSizeMB(512)) and then open a second, unrelated IndexWriter on another directory with its own brand-new default IndexWriterConfig. You expect only the first writer to change, as in 10.x, where the recipe's 'Before' form set the same values on that writer's own merge policy. Instead every config that was not given a codec holds the one default codec instance (Codec.getDefault, also what Codec.forName("Lucene104") returns), whose CompoundFormat keeps the settings in mutable fields, so the second writer writes its force-merged, naturally merged and addIndexes(CodecReader) segments non-compound, and the settings stay after the first writer closes.

## Expected vs actual

- Expected: You expect only the first writer to change, as in 10.x, where the recipe's 'Before' form set the same values on that writer's own merge policy.
- Actual: Every config that was not given a codec holds the one default codec instance (Codec.getDefault, also what Codec.forName("Lucene104") returns), whose CompoundFormat keeps the settings in mutable fields, so the second writer writes its force-merged, naturally merged and addIndexes(CodecReader) segments non-compound, and the settings stay after the first writer closes.

## Files

- [src/org/apache/lucene/index/TestCompoundFormatRecipeScope.java](src/org/apache/lucene/index/TestCompoundFormatRecipeScope.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCompoundFormatRecipeScope
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
