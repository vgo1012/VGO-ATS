"""静态依赖检查及 SQLite 合成数据演练；不连接业务库，不替代 MySQL 验证。"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[2]
schema = (ROOT / "docs/sql/hr_ai_complete_schema.sql").read_text(encoding="utf-8")
sql = Path(__file__).with_name("reset-recruitment-data.sql").read_text(encoding="utf-8")
tables = dict(re.findall(r"CREATE TABLE\s+`?(\w+)`?\s*\((.*?)\) ENGINE=", schema, re.S))
manifest = {
    name: (int(order), predicate.replace("''", "'"))
    for name, order, predicate in re.findall(r"^    \('([^']+)', (\d+), '((?:[^']|'')*)'\)[,;]?$", sql, re.M)
}
assert set(manifest) == set(tables), "清单必须覆盖基线全部表，新增表必须人工复核"
assert "CALL hr_reset_recruitment_data(NULL, FALSE, NULL);" in sql
assert not re.search(r"TRUNCATE|FOREIGN_KEY_CHECKS\s*=\s*0", sql, re.I)
assert "ROLLBACK;" in sql and "RESIGNAL;" in sql
assert sql.index("IF p_execute = 0 THEN") < sql.index("UPDATE talent_resume_intake SET")
update_statements = re.findall(r"^\s*UPDATE\s+[^;]+;", sql, re.M)
assert all(re.search(r"\bWHERE\b", statement, re.I) for statement in update_statements), "UPDATE 必须有明确 WHERE，避免客户端拒绝无条件更新"
unlink_updates = re.findall(r"    UPDATE (\w+) SET (\w+) = NULL(?: WHERE ([^;]+))?;", sql)
assert all(condition == f"{col} IS NOT NULL" for _, col, condition in unlink_updates), "解链只更新非空关联"
updates = [(name, col) for name, col, _ in unlink_updates]
fks = {}
for name, body in tables.items():
    fks[name] = re.findall(r"FOREIGN KEY\s*\(([^)]+)\)\s*REFERENCES\s+`?(\w+)`?\s*\(([^)]+)\)", body)
    for cols, parent, _ in fks[name]:
        if manifest[name][0] == 0:
            assert manifest[parent][0] == 0, (name, parent, "保留表引用待清理表")
        elif (name, cols.strip()) not in updates:
            assert name != parent, (name, "自引用未解链")
            assert manifest[parent][0] == 0 or manifest[name][0] < manifest[parent][0], (name, parent)
for name, col in updates:
    definition = re.search(r"^\s*`?" + col + r"`?\s+([^\n]+)", tables[name], re.M)
    assert definition and "NULL" in definition[1] and "NOT NULL" not in definition[1]
    assert manifest[name][1] == "1=1", "不可解链保留记录"

# 只使用合成标识，建 SQLite 外键模型，执行脚本实际清单与解链语句。
db = sqlite3.connect(":memory:")
columns = {}
for name, body in tables.items():
    columns[name] = set(re.findall(r"^\s*`?(\w+)`?\s+(?:CHAR|VARCHAR|INT|BIGINT|TEXT|JSON|BOOLEAN|SMALLINT|TINYINT|TIMESTAMP|DATETIME|DATE|DECIMAL|BINARY|VARBINARY|LONGBLOB)\b", body, re.M | re.I))
    for cols, parent, refs in fks[name]:
        columns[name].update(c.strip() for c in cols.split(","))
for refs in fks.values():
    for _, parent, cols in refs:
        columns[parent].update(c.strip() for c in cols.split(","))
for name in tables:
    unique = {tuple(c.strip() for c in refs.split(",")) for group in fks.values() for _, parent, refs in group if parent == name}
    parts = ['"' + col + '" TEXT' for col in sorted(columns[name])]
    parts += ["UNIQUE (" + ",".join('"' + c + '"' for c in cols) + ")" for cols in unique]
    parts += [f"FOREIGN KEY ({cols}) REFERENCES {parent} ({refs})" for cols, parent, refs in fks[name]]
    db.execute(f'CREATE TABLE "{name}" (' + ",".join(parts) + ")")

def insert(name, overrides=None):
    values = {c: "business" for c in columns[name]}
    values.update(overrides or {})
    db.execute(f'INSERT INTO "{name}" (' + ",".join('"' + c + '"' for c in values) + ") VALUES (" + ",".join("?" for _ in values) + ")", list(values.values()))

for name in tables:
    insert(name)
prefix = "000000000000000000000000"
insert("matching_jd_match_plan", {"id": prefix + "11", "jd_version_id": None})
insert("matching_controlled_criterion_plan_version", {"id": prefix + "13", "jd_version_id": None})
insert("matching_jd_match_plan_version", {"id": prefix + "12", "plan_id": prefix + "11", "jd_version_id": None, "controlled_criterion_plan_version_id": prefix + "13"})
insert("matching_controlled_criterion_definition", {"id": "company-criterion", "plan_version_id": prefix + "13"})
insert("matching_controlled_criterion_plan_version", {"id": "company-criteria-v2", "jd_version_id": None})
insert("matching_jd_match_plan_version", {"id": "company-plan-v2", "plan_id": prefix + "11", "jd_version_id": None, "controlled_criterion_plan_version_id": "company-criteria-v2"})
insert("matching_controlled_criterion_definition", {"id": "company-criterion-v2", "plan_version_id": "company-criteria-v2"})
insert("matching_weight_profile", {"id": prefix + "21"})
insert("matching_weight_profile_version", {"id": prefix + "22", "profile_id": prefix + "21"})
insert("matching_weight_profile_version", {"id": "company-weight-v2", "profile_id": prefix + "21"})
insert("identity_object_grant", {"id": "client-grant", "object_type": "CLIENT"})
db.commit()
db.execute("PRAGMA foreign_keys=ON")
assert not db.execute("PRAGMA foreign_key_check").fetchall()
kept = {n: db.execute(f'SELECT * FROM "{n}"').fetchall() for n, (order, _) in manifest.items() if order == 0}

def clean():
    for n, c, condition in unlink_updates:
        db.execute(f'UPDATE "{n}" SET "{c}" = NULL WHERE {condition}')
    for n, (order, predicate) in sorted(manifest.items(), key=lambda item: item[1][0]):
        if order:
            db.execute(f'DELETE FROM "{n}" WHERE ' + predicate)

before = list(db.iterdump())
db.execute("BEGIN")
clean()
db.rollback()
assert before == list(db.iterdump()), "回滚必须恢复合成模型全部数据"
clean()
db.commit()
assert not db.execute("PRAGMA foreign_key_check").fetchall()
for n, rows in kept.items():
    assert rows == db.execute(f'SELECT * FROM "{n}"').fetchall(), n
for n, (_, predicate) in manifest.items():
    assert db.execute(f'SELECT COUNT(*) FROM "{n}" WHERE ' + predicate).fetchone()[0] == 0, n
for n, expected in {"matching_jd_match_plan": 1, "matching_jd_match_plan_version": 2, "matching_weight_profile": 1, "matching_weight_profile_version": 2, "matching_controlled_criterion_definition": 2, "matching_controlled_criterion_plan_version": 2, "identity_object_grant": 1}.items():
    assert db.execute(f'SELECT COUNT(*) FROM "{n}"').fetchone()[0] == expected, n
after = list(db.iterdump())
clean()
db.commit()
assert after == list(db.iterdump()), "重复执行必须不改变保留数据"
print(f"PASS: {len(tables)} tables; FK order/nullability; retained rows; rollback; rerun (SQLite model only)")
