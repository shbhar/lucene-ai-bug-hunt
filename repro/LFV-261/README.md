# StableTflSimilarity scores SynonymQuery and CombinedFieldQuery with the length of a placeholder term string ('synonym pseudo-term', 19 code points, or 'pseudo_term', 11), not the length of the query terms

Bug LFV-261 · Area: search / similarities: StableTflSimilarity with SynonymQuery and CombinedFieldQuery · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-261.html](../../bugs/LFV-261.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set StableTflSimilarity on the searcher, whose javadoc derives a term's rarity from the length |t| of the query term, and you search with a SynonymQuery (for example the one QueryBuilder builds when a synonym filter stacks 'dog' and 'dogs' at one position) or with a CombinedFieldQuery. You expect the real query terms' length to be used, so that SynonymQuery{ab, cd} on a document that contains 'ab' once scores like TermQuery(ab), and a one-field CombinedFieldQuery scores like a TermQuery on a copy field. Instead both queries hand the similarity a fixed placeholder term ('synonym pseudo-term', 19 code points; 'pseudo_term', 11 code points), so every synonym group is scored as an almost maximally rare 19-character term and every combined-field term as an 11-character one: SynonymQuery{ab, cd} scores 1.4050181 instead of 1.3490043, and in 200-token documents the group {dog, dogs} scores 2.13x TermQuery(dog) and outranks a document that matches 'elephant'.

## Expected vs actual

- Expected: `SynonymQuery{ab, cd}` scores like `TermQuery(ab)` (0.9569799), a one-field `CombinedFieldQuery` like the copy-field `TermQuery` (1.3647432), and the "elephant" document ranks first.
- Actual: 1.4050031 (`19.0 = tl`) and 1.4047582 (`11.0 = tl`), and the "dog" document (1.4049385) outranks the "elephant" document (1.325023); 3 of 5 tests fail, the BM25 controls pass.

## Files

- [src/org/apache/lucene/search/TestStableTflPseudoTermLength.java](src/org/apache/lucene/search/TestStableTflPseudoTermLength.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestStableTflPseudoTermLength
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestStableTflQueryTermLength.java](TestStableTflQueryTermLength.java)
- [TestStableTflPseudoTermLength.java](TestStableTflPseudoTermLength.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
