#!/usr/bin/env python3
"""Unit documented-legal-values-vs-code, nullable value domain, conflict scan: a public or protected method or constructor whose javadoc @param
text says null is accepted ("or null", "If null", "may be null", "can be null", "null if", "null means", "null to")
while its own body refuses null for that parameter (Objects.requireNonNull(p...), 'if (p == null) { throw',
'p == null' followed by a throw within 3 lines).  Text scan; every hit is a lead to read, not a finding.
Usage: python3 -I nullable_conflicts.py <lucene worktree>    (all modules except sandbox, luke, benchmark, demo)"""
import os, re, sys
ROOT = os.path.join(sys.argv[1], 'lucene')
ALLOW = re.compile(r'\bor (<code>|\{@code )?null\b|[Ii]f (it is |this is )?(<code>|\{@code )?null|[Mm]ay be (<code>|\{@code )?null|[Cc]an be (<code>|\{@code )?\s*null|null(</code>|\})? (if|means|to |for|when|is allowed|is ok)|[Pp]ass (<code>|\{@code )?null|nullable|optional', re.S)
DENY = re.compile(r'(must|can|may|should|will)\s*(not|never)\s+be\s+(<code>|\{@code )?null|non-null|not null|never (<code>|\{@code )?null|cannot be (<code>|\{@code )?null|can\'t be null', re.S)
hits = []; nparams = 0
for root, dirs, files in os.walk(ROOT):
    r = root + '/'
    if '/src/java/' not in r or any(x in r for x in ('/sandbox/', '/luke/', '/benchmark', '/demo/', '/build/')): continue
    for f in sorted(files):
        if not f.endswith('.java'): continue
        p = os.path.join(root, f); rel = os.path.relpath(p, sys.argv[1]); t = open(p, encoding='utf-8', errors='replace').read()
        for m in re.finditer(r'/\*\*((?:(?!\*/).)*?)\*/\s*((?:@\w+(?:\([^)]*\))?\s*)*(?:public|protected)[^;{]*?\(([^;{]*?)\)[^;{]*)\{', t, re.S):
            doc, decl, params = m.group(1), m.group(2), m.group(3)
            # method body: brace matching from the opening brace
            i = m.end(); depth = 1
            while i < len(t) and depth:
                c = t[i]; depth += (c == '{') - (c == '}'); i += 1
            body = t[m.end():i]
            for pm in re.finditer(r'@param\s+(\w+)\s+((?:(?!@param|@return|@throws|@see|@lucene).)*)', doc, re.S):
                name, text = pm.group(1), ' '.join(pm.group(2).replace('*', ' ').split())
                if not re.search(r'\b' + re.escape(name) + r'\b', params): continue
                nparams += 1
                if not ALLOW.search(text) or DENY.search(text): continue
                refuse = re.search(r'requireNonNull\(\s*' + re.escape(name) + r'\b', body) or \
                         re.search(r'\b' + re.escape(name) + r'\s*==\s*null\s*\)\s*\{?\s*(?:\n[^\n]*){0,2}?\s*throw\b', body)
                if refuse:
                    hits.append((rel, t.count('\n', 0, m.start(2)) + 1, name, text[:160], ' '.join(decl.split())[:120], ' '.join(refuse.group(0).split())[:80]))
print('# @param texts read: %d; conflicts (javadoc allows null, the body refuses it): %d' % (nparams, len(hits)))
for h in sorted(hits): print('%s:%d\t%s\t%s\t%s\t%s' % h)
