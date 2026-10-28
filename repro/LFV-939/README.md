# The file-format javadoc of the default terms dictionary (Lucene103BlockTreeTermsWriter) gives a wrong.tim/.tmd grammar: each.tmd FieldStats still lists RootCodeLength and root-code bytes, which the trie-based terms index no longer writes, and the PostingsHeader is documented at the start of every.tim field although it is written once in.tmd, before NumFields (documentation defect; the writer and reader agree)

Bug LFV-939 · Area: codecs / terms dictionary (Lucene103BlockTreeTermsWriter.tim/.tmd file-format javadoc vs its writer and reader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode the terms metadata (.tmd) and terms dictionary (.tim) files of the default codec by following the Lucene103BlockTreeTermsWriter javadoc, which says.tmd is Header, NumFields, FieldStats... with a RootCodeLength and root-code bytes in each FieldStats, and that every.tim field starts with a PostingsHeader. The writer instead puts the postings writer's header (an IndexHeader plus the block size) once into.tmd, right after its own header and before NumFields, writes no PostingsHeader into.tim and, since the trie terms index of 10.3.0, writes no root code; a parser built from the text reads NumFields as 63 (the first byte of the codec magic) and, even with that header skipped, takes SumTotalTermFreq for RootCodeLength. Lucene's own reader matches the writer, so nothing breaks at runtime; only the documentation is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/codecs/lucene103/blocktree/TestLucene103BlockTreeJavadocGrammar.java](src/org/apache/lucene/codecs/lucene103/blocktree/TestLucene103BlockTreeJavadocGrammar.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.codecs.lucene103.blocktree.TestLucene103BlockTreeJavadocGrammar
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
