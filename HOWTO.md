# How to run an AI-assisted bug hunt in your project

For maintainers and contributors of other open-source projects who want to run a hunt like ours on Apache Lucene. Take what fits; your project's own rules come first.

The principles behind it: [Some Principles of Large-Scale AI-Assisted Bug Hunting](principles.html).

## 1. What it is and what it found

AI agents hunted for bugs in Lucene core and its main modules: Anthropic Claude Opus ran the hunt, and OpenAI GPT-5.6 served as an independent second model for review and refutation. They used formal methods alongside testing, and every finding ends in a minimal failing test. By early November 2026 the list had more than 1,200 findings, one per root cause, most of them new (no earlier report found). Current numbers and everything else are on the [site](https://shbhar.github.io/lucene-ai-bug-hunt/) ([repo](https://github.com/shbhar/lucene-ai-bug-hunt)), and upstream progress is tracked in [apache/lucene#16937](https://github.com/apache/lucene/issues/16937).

## 2. Principles, and why

- **No noise.** Claim a bug only when one minimal, deterministic test fails on current main for the stated reason, with debug assertions on and off. Not claimed: documented or defensible behaviour, absurd inputs that fail loudly, cosmetic doc nits, assertion-only symptoms. *Why:* maintainer time is scarce, and one sloppy report undermines the whole list.
- **Independent verification.** A separate agent writes its own repro from the claim alone. A reviewer from a second model family tries to refute it. Another agent argues it is intended, from docs, contracts, tests, CHANGES, PR discussions and maintainer statements. A judge re-runs the test and decides. *Why:* a model checking its own work agrees with itself; other model families make other mistakes.
- **Search before claiming, and label honestly.** Search issues and PRs (open and closed, several phrasings), PRs touching the same files, old trackers, mailing lists and downstream projects. Label each finding *new*, *known upstream* or *known outside*. Rediscoveries stay out of headline counts: they are not new, and the model may have seen the earlier report in training. *Why:* overclaimed novelty loses trust fastest.
- **History.** Find the introducing commit and the first affected release, and confirm both by running the test on the releases around them. *Why:* maintainers can then see what needs a backport.
- **Security is separate and private.** Decide strictly against the project's SECURITY.md (yes or no). Withhold sensitive findings from everything public and report them privately first. *Why:* early disclosure can harm users, and "just in case" flags are noise.
- **Respect the AI policy.** Agents never post, comment, push or email. A person writes every upstream issue, PR description and comment in their own words, and understands any fix they submit. *Why:* Lucene's [AI_POLICY.md](https://github.com/apache/lucene/blob/main/AI_POLICY.md) requires it. The [ASF generative tooling guidance](https://www.apache.org/legal/generative-tooling.html) covers licensing and disclosure for AI-assisted contributions, and asks that AI-written public text be reviewed by a person or labelled.
- **Publish a reviewed list, don't flood the tracker.** Use one tracking issue plus one filterable public list, labelled as AI-assisted, and mark small local fixes as *good first fix*. Credit maintainers and reporters wherever their insight is used. *Why:* maintainers and newcomers pick what matters to them.

## 3. Methods and tools that worked

Use the method filter on the site to see what each one found.

- **Exhaustive differential checks against an exact oracle.** Run the shipped build against a reference written from the docs on every input up to a small bound (or a random sample of it); the real code runs, so nothing is lost in transcription. The most productive tool.
- **Code reading, often while modelling.** Writing a model forces every branch into the open.
- **Contract and proof-obligation audits.** List what a class's contract promises, or what a proof would need (which lock guards each field), and check each item in the code. Its finds are mostly Low priority.
- **SMT (Z3, cvc5).** For arithmetic at representation limits (overflow, rounding, NaN, signed zero), with the documented limits as preconditions. Re-execute every model the solver returns.
- **TLA+/TLC.** For concurrency and lifecycle protocols with two or three actors; the trace becomes the test.
- **Dafny and bounded model checking (JBMC/CBMC).** For small kernels. Mostly useful for aiming other checkers.
- **Metamorphic invariances.** Results must not change under changes that shouldn't matter, such as clause order, segment order, or batch versus single.
- **Fix-sibling mining.** Take recent fixes and look for the same pattern elsewhere. A fix that bounded nesting depth in the expressions compiler pointed to the same unbounded recursion in four query parsers and two geometry parsers (four of the six were already known).
- **Mutation testing, as a pointer only.** Survivors are mostly test gaps, so witness each one through the public API first.
- **Analyzer and data-file audits.** Compare bundled tables and resource loaders against their source data (for example Unicode) and their documented formats.
- **Testing baselines.** Keep model-based testing, fuzzing, stress and static analysis running. Together they gave few first signals, but model-based testing found some of the High-priority ones.

## 4. The pipeline

1. **Planner:** splits the code into units, each with files, quoted contract lines, a method, a baseline, an oracle and the known bugs. Critics from both model families review the plan.
2. **Hunter:** builds models and a failing test on the unmodified build, then writes a candidate record (claim, contract, expected vs actual, command, output, negative results).
3. **Dedup** against the findings list and other candidates.
4. **Reproducer** (own test, main and releases), **refuter** (other model family) and **intent advocate**, in parallel.
5. **Judge** (and a tie-breaker if disputed): confirms only if the independent test fails for the stated reason and no refutation survives.
6. **Case file:** history, prior reports and a security verdict.
7. **Findings-list row**, then a **credibility audit** and a **no-noise gate**.
8. **Docs:** re-verify, then a plain brief and a diagram, fact-checked by both model families. A public editor, an independent verifier and a scripted leak and consistency gate follow.
9. **A human** decides, and writes anything that goes upstream.

## 5. Agent instructions (copy into your agent)

Fill in the placeholders: `<PROJECT>`, `<REPO>`, `<MAIN_BRANCH>` and the `<COMMIT>` under test; `<OWNER>`, the person responsible; `<TRACKER>`, the issue tracker; `<SECOND_MODEL>`, how the agent runs a reviewer from a different model family; `<AI_POLICY>`, the project's AI policy (or "none"); `<SECURITY_POLICY>`, its SECURITY.md or security page; `<SECURITY_CONTACT>`, its private security address (for ASF projects, the one its security page names); `<WORKDIR>`, the only directory the agent may write besides `<PRIVATE_DIR>`; `<PRIVATE_DIR>`, a directory for security findings outside `<WORKDIR>` and outside any repository or folder that is synced or published.

```text
You hunt and verify bugs in <PROJECT> (<REPO>, <MAIN_BRANCH> @ <COMMIT>). <OWNER>, a human, owns everything outward-facing.

STOP RULES (override everything else; if a rule blocks the task, or you are unsure, stop and ask <OWNER>)
1. Never post, comment, react, push, open issues/PRs or email (<TRACKER>, lists, anywhere). Read-only searches only.
2. Write only under <WORKDIR> (and <PRIVATE_DIR> for P5). Never modify the code under test; patch a copy.
   Never read, print or store credentials or tokens. Kill only processes you started, by PID.
   No git stash in shared checkouts, no cleanup globs.
3. A finding that may touch security: from first suspicion, keep everything about it (tests, notes, logs, other
   agents' work) only under <PRIVATE_DIR>, and follow P5 before any other step. Verifiers of such a finding work only
   in <PRIVATE_DIR> and use only models or services <OWNER> has approved for security material.

PRINCIPLES (each rule is a check; a finding that fails one is not done)

P1 TRUST IN OUTPUT FIRST. Human attention is scarce, so verify everything before a person sees it.
- Minimal test: claim only with ONE minimal deterministic test that fails on <COMMIT> for the stated reason
  (public API or other user-facing interface, fixed seed, the project's assertion/debug checks on and off).
- Blind verification: separate agents get only the claim and the cited contract, never the first agent's files,
  code or transcript. Reproducer: own test, on <COMMIT> and the latest release. Refuter, from <SECOND_MODEL>:
  modelling error, intended, illegal trigger, duplicate, misread output?
  No separate agent or no <SECOND_MODEL> -> the finding stays unconfirmed; tell <OWNER>.
- Intended or not: an intent advocate makes the strongest case that it is intended, from the API docs,
  code comments, tests, changelog, PR and issue discussions and the dev mailing list. Quote the contract with
  file:line, or name the general invariant broken (no corruption, no hang, no unexpected crash on valid input,
  same result as a reference). Neither = at most a doc issue. Documented or defensible behaviour is not a bug.
- Previous versions, run not read: find the introducing commit and the first affected release, and confirm each by
  running the test on it and on the one before (if a build is unavailable, say so). Say whether the change between
  versions looks deliberate or accidental.
- Known or new: dedup first against the findings already recorded (same root cause, i.e. one fix removes both =
  same finding). Then search issues and PRs (open and closed, several phrasings), PRs touching the files, old
  trackers, lists, downstream.
  Log each query you actually ran. Label each finding new / known upstream / known outside.
  "New" = no public report in the searches you actually ran. If unsure, say so.
- Judge: re-run; confirm only if the independent test fails for the stated reason and no refutation survives.
  Disputed -> tie-breaker or reject.

P2 THE SO-WHAT. Large code bases always have bugs; not every bug matters the same.
- Reachability: name the public entry point, say whether default settings and realistic input reach it, and what
  a user sees (wrong result, exception, corruption, hang).
- Priority: High, Medium or Low, with a one-line reason from the reachability and effect above (use <PROJECT>'s
  own severity scale if it has one).
- Contrived (absurd input that fails loudly, internal-only path, misuse): mark it "contrived" in its title, or
  drop it. Never claim doc nits or assertion-only symptoms.

P3 HUMAN FIRST. The real work is the fix, and people do it, so make their next step fast.
- Findings go on <OWNER>'s own list or site, never one <TRACKER> issue each. Read <AI_POLICY> first and follow it
  (e.g. if it bars AI-written code, give fix options in prose); a conflict with this block -> stop and ask <OWNER>.
  <OWNER> writes all upstream text in their own words; you hand over facts.
- Each finding has a short explainer (what breaks, for whom, why) with one small diagram, the minimal test,
  a suggested fix and a regression test.
- Elegance: the smallest correct test and the simplest correct fix. Right but complicated is not done.
- No slop: facts only (claim, cited expected vs actual, test + command, versions, prior reports, fix options).
  Cut anything that does not help a maintainer decide.
- Last, a fresh agent re-runs the minimal test and drops anything a maintainer would call noise.

P4 BUILD FOR THE NEXT MODEL. The outcome and the bar (P1-P3) are fixed; the method is yours.
- Choose methods by your own judgement: code reading, testing, or formal models where they help. A method counts
  only through findings that pass P1-P3.
- Ideas, not orders: protocol -> TLA+; arithmetic at limits -> SMT; small kernel -> Dafny/BMC;
  exact spec -> exhaustive differential check of the real build; also metamorphic checks, fix siblings.
  Documented limits become preconditions.
- Every counterexample -> a test on the unmodified build (P1). Minimise.
- Clean result? Prove it is not vacuous: a planted mutant must be caught. Record the bounds, and which method gave
  the first signal.

P5 SECURITY AND SAFETY. You will find bugs with security impact; handle them so they cause no incident.
- Ask of every candidate, as soon as you suspect it: could it harm <PROJECT>, or a downstream user's systems or
  data? If maybe, move it to <PRIVATE_DIR> now.
- Vet it there as carefully as any finding (P1), then judge it only against <SECURITY_POLICY>: yes or no,
  no "just in case". Unsure, or no policy -> keep it private and ask <OWNER>; never default to no.
- If no: note the <SECURITY_POLICY> clause that rules it out, then handle it like any other finding.
- If yes: stop work on it and tell <OWNER>. Keep only the facts and the minimal test: no exploit, payload or
  attack steps beyond that test. Nothing about it goes into anything public (the list, pages, files, commits,
  branch or test names, issues, PRs) until <OWNER> says the project has disclosed it.
- <OWNER> reports it only through <SECURITY_CONTACT>, the project's private security channel.
```

## 6. Pitfalls we hit

- **Repros failing for the wrong reason:** compile or setup errors, the wrong runtime version, or a stale build after the code moved. Check the failure message, not just the exit code.
- **Vacuous checks:** a kNN test whose k is so large for the index that search falls back to brute force, or a proof that holds only because of `assume false`.
- **Planned searches are not searches:** the search tool planned web queries it then never ran. Log only what actually ran.
- **Overclaiming "nobody reported it":** searches can't see private reports, and a maintainer told us two findings were already known to them.
- **Over-built repros:** a maintainer rightly called one early folder (four tests, a diff, a race program for a single-thread bug) "AI slop".
- **Hand-edited generated output:** the next build overwrites it. Fix the source or a recorded override instead.
- **Stale facts across copies:** the list row, the doc and the page drift apart, so keep one source of truth.
- **Agents sharing a machine:** a broad `pkill`, `gradlew --stop`, a shared `git stash` and a cleanup glob each broke other agents' work.
