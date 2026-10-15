"""Produce a complete tracked-file inventory without importing game modules."""
import ast, hashlib, json, pathlib, re, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = '6216b771282d2d349ccd67ee58c378f035c83553'
rows = []
for name in subprocess.check_output(['git','ls-tree','-r','--name-only',SOURCE], cwd=ROOT, text=True).splitlines():
    p = ROOT / name
    if not p.is_file(): continue
    data = subprocess.check_output(['git','show',SOURCE+':'+name],cwd=ROOT)
    row = dict(path=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
    if p.suffix in {'.py','.pyx','.pxd','.md','.toml','.yml','.yaml','.txt'}:
        s = data.decode('utf-8', errors='replace')
        row['lines'] = len(s.splitlines())
        if p.suffix in {'.py','.pyx','.pxd'}:
            row['symbols'] = re.findall(r'^\s*(?:cdef |cpdef |async )?(?:class|def)\s+(\w+)',s,re.M)
            row['imports'] = sorted(set(re.findall(r'^\s*(?:from|import)\s+([\w.]+)',s,re.M)))
        if p.suffix == '.md': row['headings'] = re.findall(r'^#{1,6}\s+(.+)',s,re.M)
    rows.append(row)
packets = []
s = (ROOT/'shared/packet.pyx').read_text()
for m in re.finditer(r'^cdef class (\w+)\(Loader\):(.*?)(?=^cdef class |\Z)',s,re.M|re.S):
    n = re.search(r'id: int = (-?\d+)',m[2])
    if n: packets.append(dict(name=m[1],id=int(n[1])))
report = dict(source_commit=SOURCE,files=rows,packets=packets)
(ROOT/'java/docs/source-inventory.json').write_text(json.dumps(report, indent=2)+'\n')
lines = ['# Source-module inventory','', 'Structural inventory; presence does not establish functional parity.','', '| Source | Lines | Java destination | Status |','|---|---:|---|---|']
mapping={'aoslib':'world / physics','shared':'protocol / catalog','protocol':'protocol / dispatch','server':'server / simulation / services','modes':'mode','plugins':'plugin','commands':'admin','server_gui':'admin (portable replacement)','testbot':'test harness','scripts':'test tools','tests':'JUnit / differential harness'}
for row in rows:
    if pathlib.Path(row['path']).suffix in {'.py','.pyx','.pxd'}:
        dest=mapping.get(row['path'].split('/')[0], 'launcher / tooling')
        lines.append(f"| `{row['path']}` | {row.get('lines',0)} | `net.battlespades.{dest}` | pending unless parity matrix says otherwise |")
(ROOT/'java/docs/SOURCE_INVENTORY.md').write_text('\n'.join(lines)+'\n')
print(len(rows),'tracked files;',len(packets),'packet definitions;',sum(r.get('lines',0) for r in rows if pathlib.Path(r['path']).suffix in {'.py','.pyx','.pxd'}),'source lines')
