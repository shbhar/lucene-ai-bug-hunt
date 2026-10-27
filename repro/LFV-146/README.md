# Two field names that differ only in an unpaired surrogate are written to disk as the same U+FFFD name, so IndexWriter commits an index that DirectoryReader and a new IndexWriter refuse to open (on main also colliding field attribute keys and commit user-data keys)

Bug LFV-146 · Area: index / FieldInfos and segment metadata strings (DataOutput#writeString in.fnm, segments_N and.si) · Priority: medium · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document with two field names that differ only in an unpaired surrogate, for example 'f\uD800' and 'f\uFFFD' (or two different lone surrogates in the same position), and commit; addDocument and commit return normally, as IndexWriter documents that malformed UTF-16 is silently replaced with U+FFFD, and you expect the committed index to open. Instead DataOutput#writeString writes both names as the same bytes ('f' + U+FFFD), so DirectoryReader.open and a new IndexWriter throw IllegalArgumentException 'duplicate field names: 0 and 1 have: f\uFFFD' for the whole index, and CheckIndex can only recover it by dropping the whole segment. On main the same happens for two colliding field attribute keys or commit user-data keys ('duplicate key'), and a lone-surrogate index-sort field name is stored in its U+FFFD form, so a new IndexWriter with the same sort is refused.

## Expected vs actual

- Expected: You expect the committed index to open.
- Actual: DataOutput#writeString writes both names as the same bytes ('f' + U+FFFD), so DirectoryReader.open and a new IndexWriter throw IllegalArgumentException 'duplicate field names: 0 and 1 have: f\uFFFD' for the whole index, and CheckIndex can only recover it by dropping the whole segment.

## Files

- [src/org/apache/lucene/index/TestUnpairedSurrogateNameCollision.java](src/org/apache/lucene/index/TestUnpairedSurrogateNameCollision.java) (JUnit test)
- [src/org/apache/lucene/index/TestUnpairedSurrogateNameCollisionWriter.java](src/org/apache/lucene/index/TestUnpairedSurrogateNameCollisionWriter.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestUnpairedSurrogateNameCollision

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestUnpairedSurrogateNameCollisionWriter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
