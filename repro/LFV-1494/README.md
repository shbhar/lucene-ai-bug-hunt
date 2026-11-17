# SimpleTextCodec only: the stored-fields, term-vectors and doc-values/norms readers leave their file (.fld, .tvc, .dat, .len) open when a segment open fails while reading it, because the constructor's cleanup block ends before the step that reads the file (one input per failed open; every release since 4.0.0)

Finding LFV-1494 · Area: codecs / SimpleText codec readers (stored fields, term vectors, doc values and norms: cleanup when opening a segment fails) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1494.html](../../bugs/LFV-1494.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index that was written with SimpleTextCodec (Lucene's plain-text codec, which its javadoc labels 'FOR RECREATIONAL USE ONLY'), and reading the stored-fields file (.fld) fails, for example with an I/O error from the disk. You expect DirectoryReader.open to fail with that error and to close every file it had opened, as it does when the same error hits the segment's postings, points or field-infos file. Instead the open fails with the error, as it should, and leaves the .fld file open: the reader's constructor closes its input only when opening the file fails, and reads the whole file after that block, where nothing can close it; the term-vectors reader (.tvc) has the same shape, and the doc-values reader, which also reads norms (.dat, .len), has no such block at all.

## Expected vs actual

- Expected: `DirectoryReader.open` throws `CorruptIndexException` and `dir.close()` succeeds: no file is left open.
- Actual: `CorruptIndexException` is thrown, then `dir.close()` fails with `MockDirectoryWrapper: cannot close: there are still 1 open files: {_0.fld=1}`.

## Files

- [src/org/apache/lucene/codecs/simpletext/TestSimpleTextFailedOpenClosesInput.java](src/org/apache/lucene/codecs/simpletext/TestSimpleTextFailedOpenClosesInput.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (2026-11-16): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- What the test does: it writes one empty document with SimpleTextCodec (compound files off) into a MockDirectoryWrapper over a ByteBuffersDirectory and then replaces the stored-fields file _0.fld with an empty file. MockDirectoryWrapper is the test framework's wrapping Directory that keeps count of open files; it is part of the test and is what reports the open file. The test expects DirectoryReader.open to throw CorruptIndexException, which it does, and then closes the directory. It fails there with 'MockDirectoryWrapper: cannot close: there are still 1 open files: {_0.fld=1}', the same with and without assertions. The fault in this test is a damaged (emptied) file, not the read I/O error that the description above starts from; an earlier form of the test injected that read error through its own wrapping Directory and found the same file open. The term-vectors, doc-values and norms files are not part of this test. It needs the core, codecs and test-framework jars only and no extra heap.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.simpletext.TestSimpleTextFailedOpenClosesInput
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSimpleTextFailedOpenClosesInput.java](TestSimpleTextFailedOpenClosesInput.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
