#!/usr/bin/env python3
"""Census of the try/finally shapes that the five "remove boolean success flags" refactors of 11.0 rewrote
(#14633, #14668, #14913, #14995, #15134; all on branch_11x, none in a 10.x release).
The rewrite turns
    boolean success = false; try { ...; success = true; } finally { if (success == false) { CLEANUP } }
into
    try { ... } catch (Throwable t) { CLEANUP(t); throw t; }
which is equivalent only when the flag is set as the LAST thing of the try block and the try block has no other way
out. This script reads the OLD text (commit^) of every main-source file a commit touched, finds each removed flag,
cuts the try body and the finally body by brace matching, and tags the shapes for which the rewrite can change behaviour:
  B  a return / break / continue inside the try body BEFORE the last 'success = true' (old: cleanup ran on that way out)
  B0 the try body never sets the flag (it is set elsewhere, or the cleanup was unconditional on some path)
  C  code after the last 'success = true' inside the try body (old: no cleanup when THAT code throws)
  D  the finally block also acts when the flag is true ('if (success)', an else branch, or code outside the test)
  M  the flag is assigned more than once, or reset to false
  N  a 'success = true' sits inside a nested block of the try body (an if, a loop), so a normal path can leave the
     try with the flag still false (added after the first run missed LFV-1043, whose flag is set only 'if (ticket != null)')
A second pass (residual.txt) lists, per diff hunk, the removed and added lines that are NOT the boilerplate of the
rewrite once the helper names are normalized: there the CLEANUP itself changed (tag E; added after the first run
missed LFV-1263, where 'closeWhileHandlingException(indexIn, termsIn)' became 'closeWhileSuppressingExceptions(t, this)').
Column state_after_commit says per site whether the commit removed the flag line (its old line is inside a removed
range of git show -U0) or kept it. The file is plain tab-separated text without quoting: read it with split, not a CSV reader.
usage: census.py <git dir> <out.tsv> <commit> [...]     (read-only; git show only)"""
import re, subprocess, sys
GIT, OUT = sys.argv[1], sys.argv[2]
def git(*a):
    return subprocess.run(['git', '-C', GIT] + list(a), capture_output=True, text=True, check=True).stdout
def strip_comments(s):
    s = re.sub(r'/\*.*?\*/', lambda m: re.sub(r'[^\n]', ' ', m.group(0)), s, flags=re.S)
    s = re.sub(r'//[^\n]*', lambda m: ' ' * len(m.group(0)), s)
    s = re.sub(r'"(?:\\.|[^"\\\n])*"', lambda m: '"' + ' ' * (len(m.group(0)) - 2) + '"', s)
    return s
def block(s, i):
    """s[i] == '{' -> index after the matching '}'"""
    d = 0
    for j in range(i, len(s)):
        if s[j] == '{': d += 1
        elif s[j] == '}':
            d -= 1
            if d == 0: return j + 1
    return len(s)
_rr = {}
def removed_ranges(c, f):
    """old-side line ranges that commit c removes or rewrites in file f (git show -U0)"""
    if (c, f) not in _rr:
        d = git('show', '--format=', '-U0', c, '--', f)
        _rr[(c, f)] = [(int(m.group(1)), int(m.group(2) or 1)) for m in re.finditer(r'(?m)^@@ -(\d+)(?:,(\d+))? ', d)]
    return _rr[(c, f)]
rows = []
for c in sys.argv[3:]:
    subj = git('log', '-1', '--format=%s', c).strip()
    pr = re.findall(r'#(\d+)', subj)[-1]
    files = [f for f in git('show', '--format=', '--name-only', c).split() if '/src/java/' in f and f.endswith('.java')]
    for f in files:
        try: old = git('show', c + '^:' + f)
        except subprocess.CalledProcessError: continue
        new = git('show', c + ':' + f)
        nflags_new = len(re.findall(r'\bboolean\s+(\w*[sS]uccess\w*)\s*=\s*false\s*;', strip_comments(new)))
        s = strip_comments(old)
        flags = list(re.finditer(r'\bboolean\s+(\w*[sS]uccess\w*)\s*=\s*false\s*;', s))
        if len(flags) <= nflags_new and not flags: continue
        for m in flags:
            name = m.group(1)
            line = s.count('\n', 0, m.start()) + 1
            t = re.compile(r'\btry\b').search(s, m.end())
            if not t: rows.append((pr, f, line, name, '?', 'no try after the flag', '')); continue
            b = s.find('{', t.end())
            # a try-with-resources header: skip the parenthesis
            par = s.find('(', t.end())
            if par != -1 and par < b and s[t.end():par].strip() == '':
                d = 0
                for j in range(par, len(s)):
                    if s[j] == '(': d += 1
                    elif s[j] == ')':
                        d -= 1
                        if d == 0: break
                b = s.find('{', j)
            e = block(s, b)
            body = s[b + 1:e - 1]
            rest = s[e:]
            # skip catch blocks
            pos = e
            while True:
                mm = re.match(r'\s*catch\s*\(', s[pos:])
                if not mm: break
                cb = s.find('{', pos + mm.end()); pos = block(s, cb)
            fm = re.match(r'\s*finally\s*\{', s[pos:])
            fin = ''
            if fm:
                fb = pos + fm.end() - 1; fe = block(s, fb); fin = s[fb + 1:fe - 1]
            tags = []
            sets = [x for x in re.finditer(r'\b%s\s*=\s*true\s*;' % re.escape(name), body)]
            resets = len(re.findall(r'\b%s\s*=\s*false\s*;' % re.escape(name), body))
            if not sets:
                tags.append('B0')
                last = 0
            else:
                last = sets[-1].end()
                before = body[:sets[-1].start()]
                # exits before the flag is set, at any depth (reviewed by hand afterwards)
                ex = re.findall(r'\b(return|break|continue)\b', before)
                if ex: tags.append('B(' + ','.join(sorted(set(ex))) + ')')
                after = body[last:]
                code = re.sub(r'[\s}]+', '', after)
                if code and not re.fullmatch(r'return[^;]*;', code): tags.append('C')
            if len(sets) > 1 or resets: tags.append('M')
            for x in sets:
                depth = body.count('{', 0, x.start()) - body.count('}', 0, x.start())
                if depth > 0:
                    tags.append('N'); break
            if fin:
                f2 = re.sub(r'\s+', ' ', fin).strip()
                neg = re.match(r'^if \((?:%s == false|!%s|false == %s)\) \{' % (name, name, name), f2)
                if not neg: tags.append('D')
                elif re.search(r'\}\s*else\b', f2) or not f2.endswith('}') : tags.append('D')
                else:
                    # code after the closing brace of the negative test
                    i0 = f2.find('{'); i1 = block(f2, i0)
                    if f2[i1:].strip(): tags.append('D')
            else:
                tags.append('nofinally')
            still = 'flag removed' if any(a <= line < a + n for a, n in removed_ranges(c, f)) else 'flag kept'
            excerpt = re.sub(r'\s+', ' ', body[max(0, last - 60):last + 120]).strip()
            rows.append((pr, f, line, name, still, ' '.join(tags) or 'A', excerpt[:180]))
with open(OUT, 'w', encoding='utf-8') as o:
    o.write('pr\tfile\told_line\tflag\tstate_after_commit\ttags\taround_last_set\n')
    for r in rows: o.write('\t'.join(str(x) for x in r) + '\n')
# ---- second pass: hunks whose non-boilerplate lines differ after normalizing the helper names
def norm(l):
    l = re.sub(r'\s+', '', l)
    l = l.replace('closeWhileSuppressingExceptions(t,', 'CLOSE(').replace('closeWhileHandlingException(', 'CLOSE(')
    l = l.replace('deleteFilesSuppressingExceptions(t,', 'DELETE(').replace('deleteFilesIgnoringExceptions(', 'DELETE(')
    l = l.replace('IOUtils.', '')
    return l
BOILER = re.compile(r'^(boolean\w*[sS]uccess\w*=false;|\w*[sS]uccess\w*=true;|\}finally\{|if\((!\w*[sS]uccess\w*|\w*[sS]uccess\w*==false|false==\w*[sS]uccess\w*)\)\{|\}|\{|try\{|\}catch\(Throwablet\)\{|throwt;|//.*|\*.*|/\*.*|import.*)$')
res = []
nh = 0
for c in sys.argv[3:]:
    pr = re.findall(r'#(\d+)', git('log', '-1', '--format=%s', c))[-1]
    d = git('show', '--format=', '-U0', c, '--', '*/src/java/*')
    cur = None; hunk = None
    def flush():
        global nh
        if not hunk: return
        rem = [norm(l) for l in hunk[1]]; add = [norm(l) for l in hunk[2]]
        rem = [l for l in rem if l and not BOILER.match(l)]; add = [l for l in add if l and not BOILER.match(l)]
        r2 = [l for l in rem if l not in add]; a2 = [l for l in add if l not in rem]
        # re-indented code moves are equal after whitespace removal, so what is left changed
        if r2 or a2:
            nh += 1
            res.append('== #%s %s %s' % (pr, cur, hunk[0]))
            res.extend('  - ' + l[:230] for l in r2); res.extend('  + ' + l[:230] for l in a2)
    for line in d.splitlines():
        if line.startswith('diff --git'):
            flush(); hunk = None; cur = line.split(' b/')[-1]
        elif line.startswith('@@'):
            flush(); hunk = [line.split('@@')[1].strip(), [], []]
        elif hunk is not None and line.startswith('-') and not line.startswith('---'): hunk[1].append(line[1:])
        elif hunk is not None and line.startswith('+') and not line.startswith('+++'): hunk[2].append(line[1:])
    flush(); hunk = None
open(OUT.replace('sites.tsv', 'residual.txt'), 'w', encoding='utf-8').write('\n'.join(res) + '\n')
print('residual.txt:', nh, 'hunks with lines that are not the boilerplate of the rewrite')
import collections
cnt = collections.Counter()
def shape(t):
    return 'A (flag set last, plain finally)' if t == 'A' else ('risky (B, C or N)' if re.search(r'[BCN]', t) else 'D only (finally also acts on success)')
for r in rows: cnt[(r[4], shape(r[5]))] += 1
print(OUT, len(rows), 'flag sites in the old text of', len({r[1] for r in rows}), 'files;', sum(1 for r in rows if r[4] == 'flag removed'), 'flags removed by the commits')
for k in sorted(cnt): print('  %s | %s: %d' % (k[0], k[1], cnt[k]))
for r in rows:
    if r[4] == 'flag removed' and re.search(r'[BCN]', r[5]): print('  RISKY #%s %s:%s %s' % (r[0], r[1].split('org/apache/lucene/')[-1], r[2], r[5]))
