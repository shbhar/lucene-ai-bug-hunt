# Independent evaluator for ExplainSweep output (one JSON explanation tree per line).
#
# Checks, for every node of every explained hit:
#  (F) 'computed as <formula> from:' (and leaf 'computed as <formula>'): evaluate <formula> on the
#      node's own children (each child's leading symbol, e.g. 'tfn, normalized term frequency' -> tfn;
#      a leaf formula uses its siblings) and compare with the node's value;
#  (C) combiners: 'sum of:', 'product of:', 'max of:', 'min of:', 'max plus X times others of:',
#      '..., result of:' (single child) against their children;
#  (L) labels: on single-term queries (f:a, p:a) every child whose label names an index statistic
#      (n = docFreq, N = docCount, F = totalTermFreq, T = sumTotalTermFreq, avgdl/avgfl, '+ 1'
#      variants) must show that statistic, computed here from the index statistics the harness
#      printed (not from the explanation).
# Tolerance: relative 1e-4 (float rounding of intermediate values), absolute 1e-6.
#
# Usage: python3 -I eval_sweep.py sweep.jsonl
import json, math, re, sys, collections

REL, ABS = 1e-4, 1e-6
def close(a, b):
    return math.isclose(a, b, rel_tol=REL, abs_tol=ABS)

def sigmoid(x):
    return 1.0 / (1.0 + math.exp(-x)) if x >= 0 else math.exp(x) / (1.0 + math.exp(x))
def logit(p):
    return math.log(p / (1.0 - p))
FUNS = {"log2": math.log2, "log": math.log, "exp": math.exp, "sqrt": math.sqrt, "pow": pow,
        "abs": abs, "sigmoid": sigmoid, "logit": logit}

ROLE = [  # child description prefix (class name) -> symbol used by the parent's formula
    (r"^Normalization\w*,", "NORM"), (r"^Normalization\$NoNormalization|^NoNormalization", "NORM"),
    (r"^BasicModel\w*,", "BASIC"), (r"^AfterEffect\w*,", "AFTER"), (r"^Lambda\w*,", "LAMBDA"),
    (r"^Distribution\w*", "DIST"), (r"^(weight|score)\(", "score"), (r"^sigmoid calibration", "score"),
    (r"^(phraseFreq|termFreq|freq)=", "freq"),
]
ALIAS = {"Query boost": "boost", "current value": "value", "term weight": "term_weight",
         "document norm": "document_norm", "collection probability": "P_coll"}
FORMULA_REWRITE = [
    ("basicModel.score(stats, tfn)", "BASIC"), ("afterEffect.score(stats, tfn)", "AFTER"),
    ("distribution.score(stats, normalization.tfn(stats, freq, docLen), lambda.lambda(stats))", "DIST"),
    ("term weight", "term_weight"), ("document norm", "document_norm"), ("Math.pow", "pow"),
    ("Math.log", "log"), ("^", "**"),
]

def symbol(desc):
    for pat, sym in ROLE:
        if re.match(pat, desc):
            return sym
    for k, v in ALIAS.items():
        if desc == k or desc.startswith(k + ","):
            return v
    m = re.match(r"\s*([A-Za-z_][A-Za-z_0-9]*)\s*(,|$)", desc)
    return m.group(1) if m else None

def env_of(kids):
    env = {}
    for k in kids:
        s = symbol(k["d"])
        if s and s not in env:
            env[s] = float(k["v"])
    return env

def desc_env(kids):
    """symbols shown anywhere below the node (grandchildren and deeper), nearest first"""
    env, frontier = {}, list(kids)
    while frontier:
        nxt = []
        for k in frontier:
            for g in k["k"]:
                s = symbol(g["d"])
                if s and s not in env:
                    env[s] = float(g["v"])
                nxt.append(g)
        frontier = nxt
    return env

def ev(expr, env):
    e = expr
    for a, b in FORMULA_REWRITE:
        e = e.replace(a, b)
    e = re.sub(r"\blambda\b", "lambda_", e)
    env = {("lambda_" if k == "lambda" else k): x for k, x in env.items()}
    names = set(re.findall(r"[A-Za-z_][A-Za-z_0-9]*", e)) - set(FUNS)
    missing = sorted(names - set(env))
    if missing:
        return None, "missing " + ",".join(missing)
    try:
        return float(eval(e, {"__builtins__": {}}, {**FUNS, **env})), None
    except Exception as x:  # noqa
        return None, "eval error " + str(x)

def doc_lengths_and_tf(case_field):
    docs = {"f": ["a b c a a", "a d e", "f g h", "a a b", "x y z w v", "b a", "a b a b c d e f g h i j k"],
            "p": ["a b a", "a c", "b a a a", "c"]}[case_field]
    return [len(d.split()) for d in docs], [d.split().count("a") for d in docs]

LABELS = [  # (regex on the child label, function(stats, doc) -> expected value)
    (r"^n, number of documents containing term \+ 1$", lambda st, d: st["df_a"] + 1),
    (r"^n, number of documents containing term$", lambda st, d: st["df_a"]),
    (r"^docFreq, number of documents containing term$", lambda st, d: st["df_a"]),
    (r"^N, total number of documents with field$", lambda st, d: st["docCount"]),
    (r"^docCount, total number of documents with field$", lambda st, d: st["docCount"]),
    (r"^F, +total number of occurrences of term across all (documents|docs) \+ 1$", lambda st, d: st["ttf_a"] + 1),
    (r"^F, +total number of occurrences of term across all (documents|docs)$", lambda st, d: st["ttf_a"]),
    (r"^T, total number of tokens", lambda st, d: st["sumTotalTermFreq"]),
    (r"^avg(dl|fl), average length of field", lambda st, d: st["sumTotalTermFreq"] / st["docCount"]),
    (r"^(dl|fl), (length of field|field length of the document)$", lambda st, d: st["_dl"][d]),
    (r"^(freq|tf), (number of )?occurrences of term (with)?in (the )?document$", lambda st, d: st["_tf"][d]),
    (r"^P, probability that the current term is generated by the collection$",
     lambda st, d: (st["ttf_a"] + 1) / (st["sumTotalTermFreq"] + 1)),
]

INDEP = {"IndependenceChiSquared": lambda f, e: (f - e) ** 2 / e,
         "IndependenceSaturated": lambda f, e: (f - e) / e,
         "IndependenceStandardized": lambda f, e: (f - e) / math.sqrt(e)}

stats_counter = collections.Counter()
mismatches = collections.OrderedDict()
unevaluated = collections.Counter()

def report(kind, case, query, doc, msg, key2):
    key = (kind, key2)
    mismatches.setdefault(key, []).append((case, query, doc, msg))

def check_tree(rec):
    case, query, doc, st = rec["case"], rec["query"], rec["doc"], rec["stats"]
    boost = 2.5 if query.endswith("^2.5") else 1.0
    single_term = query in ("f:a", "(f:a)^2.5", "p:a", "(p:a)^2.5")
    if single_term:
        fld = query.strip("(")[0]
        dl, tf = doc_lengths_and_tf(fld)
        st = dict(st, _dl=dl, _tf=tf)

    def walk(n, parent_env):
        d, v, kids = n["d"], float(n["v"]), n["k"]
        env = env_of(kids)
        # (F) formula nodes
        m = re.search(r"computed as (.*?) from:\s*$", d)
        leaf = None
        if not m and not kids:
            leaf = re.search(r"computed as (.*?)\s*$", d)
        if m or leaf:
            formula = (m or leaf).group(1)
            e2 = dict(parent_env) if leaf else {**desc_env(kids), **parent_env, **env}
            implicit_boost = "boost" not in e2
            if implicit_boost:
                e2["boost"] = 1.0  # a similarity omits the boost child when it is 1
            if formula.startswith("independence.score"):
                ind = re.search(r"DFI\((\w+)\)", case)
                if ind and "freq" in e2 and "expected" in e2:
                    val, err = INDEP["Independence" + ind.group(1)](e2["freq"], e2["expected"]), None
                else:
                    val, err = None, "opaque independence"
            elif "n^alpha" in formula:
                # LogOddsFusionQuery: n, alpha and the (weighted) mean logit are not shown as children;
                # the sweep uses n = 2 clauses, alpha = 0.5, weights (0.7, 0.3) for the weighted case.
                ps = [float(k["v"]) for k in kids]
                sp = lambda x: math.log1p(math.exp(x)) if x < 30 else x
                w = [0.7, 0.3] if formula.startswith("sigmoid(weighted") else [0.5, 0.5]
                as_doc = sigmoid(sum(wi * logit(p) for wi, p in zip(w, ps)) * 2 ** 0.5)
                as_softplus = sigmoid(sum(wi * sp(logit(p)) for wi, p in zip(w, ps)) * 2 ** 0.5)
                stats_counter["logodds nodes: matches mean(logit)"] += close(as_doc, v)
                stats_counter["logodds nodes: matches mean(softplus(logit))"] += close(as_softplus, v)
                val, err = None, "operands not shown (n, alpha, mean logit); see logodds counters"
            elif "score == NaN" in formula:
                val, err = None, "operands not shown"
            else:
                val, err = ev(formula, e2)
                if val is not None and implicit_boost and not close(val, v) and boost != 1.0:
                    e3 = dict(e2, boost=boost)
                    v3, _ = ev(formula, e3)
                    if v3 is not None and close(v3, v):
                        val = v3  # boost applied but not shown as a child
            stats_counter["formula nodes seen"] += 1
            if val is None:
                unevaluated[(formula[:70], err)] += 1
            else:
                stats_counter["formula nodes evaluated"] += 1
                if not close(val, v):
                    report("F", case, query, doc, "node '%s' formula '%s' on children %s gives %.7g, node shows %.7g"
                           % (d.split(",")[0], formula, {k: round(x, 7) for k, x in e2.items()}, val, v), (re.sub(r"\(.*", "", d.split(",")[0]), formula))
        # (C) combiners
        mk = [float(k["v"]) for k in kids if k["m"]]
        if kids and re.search(r"(^|, )sum of:$", d) and "computed" not in d:
            stats_counter["sum nodes"] += 1
            if not close(sum(mk), v):
                report("C", case, query, doc, "sum of | children %s sum %.7g, node %.7g" % (mk, sum(mk), v), ("sum", case.split(" ")[0]))
        elif kids and re.search(r"(^|, )product of:$", d):
            stats_counter["product nodes"] += 1
            p = math.prod(float(k["v"]) for k in kids)
            if not close(p, v):
                report("C", case, query, doc, "product of | children %s product %.7g, node %.7g" % ([k["v"] for k in kids], p, v), ("product", case.split(" ")[0], re.sub(r"[0-9.]+", "#", d)[:60]))
        elif kids and d == "max of:":
            stats_counter["max nodes"] += 1
            if not close(max(mk), v):
                report("C", case, query, doc, "max of | children %s, node %.7g" % (mk, v), ("max",))
        elif kids and d == "min of:":
            stats_counter["min nodes"] += 1
            if not close(min(mk), v):
                report("C", case, query, doc, "min of | children %s, node %.7g" % (mk, v), ("min",))
        elif kids and re.match(r"max plus ([0-9.Ee+-]+) times others of:$", d):
            t = float(re.match(r"max plus ([0-9.Ee+-]+) times", d).group(1))
            stats_counter["dismax nodes"] += 1
            exp = max(mk) + t * (sum(mk) - max(mk))
            if not close(exp, v):
                report("C", case, query, doc, "dismax | children %s tie %g gives %.7g, node %.7g" % (mk, t, exp, v), ("dismax",))
        elif kids and d.endswith("result of:") and len(kids) == 1:
            stats_counter["result-of nodes"] += 1
            if not close(float(kids[0]["v"]), v):
                report("C", case, query, doc, "result of | child %.7g, node %.7g" % (float(kids[0]["v"]), v), ("result-of", re.sub(r"[0-9.]+", "#", d)[:60]))
        # (L) labels on single-term queries
        if single_term:
            for k in kids:
                for pat, fn in LABELS:
                    if re.match(pat, k["d"]):
                        stats_counter["labelled statistics checked"] += 1
                        exp = fn(st, doc)
                        if not close(float(k["v"]), exp):
                            report("L", case, query, doc, "label '%s' | shows %s, index statistic is %s (under node '%s')"
                                   % (k["d"], k["v"], exp, d.split(",")[0]), (k["d"], re.sub(r"\(.*", "", d.split(",")[0])))
                        break
        for k in kids:
            walk(k, env)

    walk(rec["expl"], {})

n = 0
for ln in open(sys.argv[1], encoding="utf-8"):
    rec = json.loads(ln)
    n += 1
    check_tree(rec)

print("explained hits:", n)
for k, c in stats_counter.items():
    print("%s: %d" % (k, c))
print("unevaluated formula kinds:")
for (f, e), c in sorted(unevaluated.items()):
    print("   %4d  %s  [%s]" % (c, f, e))
print("mismatch groups:", len(mismatches), "total:", sum(len(x) for x in mismatches.values()))
for key, rows in mismatches.items():
    case, query, doc, msg = rows[0]
    cases = sorted(set(r[0] for r in rows))
    print("[%s] %d x over %d cases (e.g. %s) key=%s\n      e.g. query=%s doc=%s: %s" % (key[0], len(rows), len(cases), ", ".join(cases[:4]), key[1], query, doc, msg))
