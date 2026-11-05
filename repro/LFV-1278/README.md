# The default compound reader's listAll() (Lucene90CompoundReader) returns the segment's sub-file names in HashMap order, although Directory.listAll() requires sorted String#compareTo order and every other in-tree Directory, including SimpleText's compound reader, sorts (since 6.0.0)

Finding LFV-1278 · Area: core / codecs (default compound file format: Lucene90CompoundReader, CompoundDirectory.listAll) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1278.html](../../bugs/LFV-1278.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a segment's compound file with the default codec (si.getCodec().compoundFormat().getCompoundReader(dir, si)) and call listAll(). You expect the names in sorted String#compareTo order, because the reader is a Directory, Directory.listAll() says 'The output must be in sorted (UTF-16, java's String#compareTo) order', and every other in-tree Directory, SimpleText's compound reader included, sorts. Instead Lucene90CompoundReader.listAll() returns the right names in HashMap iteration order (for a one-document segment on main the first name is _0_Lucene104_0.doc, where the sorted listing starts with _0.fdm), with no error.

## Expected vs actual

- Expected: `listAll()` returns `[_0.fdm, _0.fdt, _0.fdx, _0.fnm]`, sorted.
- Actual: `[_0.fnm, _0.fdx, _0.fdt, _0.fdm]: arrays first differed at element [0]; expected:<_0.f[d]m> but was:<_0.f[n]m>` (`TestCompoundListAllSorted.java:46`).

## Files

- [src/org/apache/lucene/codecs/lucene90/TestCompoundListAllSorted.java](src/org/apache/lucene/codecs/lucene90/TestCompoundListAllSorted.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestCompoundListAllSorted
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCompoundListAllSorted.java](TestCompoundListAllSorted.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
