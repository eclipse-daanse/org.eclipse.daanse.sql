# Daanse SQL Guard

`SqlGuard` narrows an **untrusted** SQL string down to a **read-only query over an
explicitly whitelisted catalog**. It is the trust boundary between caller-supplied
SQL (for example a drill-through / pass-through query from an analytics client) and
the database.

- **API:** `guard/api` — `SqlGuard`, `SqlGuardFactory`, the `elements` catalog model,
  and the `GuardException` hierarchy.
- **Implementation:** `guard/jsqltranspiler` — `TranspilerSqlGuard`, built on JSqlParser
  and JSQLTranspiler.

## What the guard guarantees

`SqlGuard.guard(String)` either returns an equivalent read-only `SELECT` — with every
table and column fully qualified against the whitelisted catalog and rendered in the
configured dialect — or throws a `GuardException`. It rejects, by construction:

- any non-`SELECT` statement (INSERT / UPDATE / DELETE / DDL / …);
- a **data-modifying CTE**, e.g. `WITH x AS (UPDATE foo …) SELECT …`;
- `SELECT … INTO` (writes a table) and `SELECT … FOR UPDATE` / `FOR SHARE` (row locks);
- any reference to a table or column **outside the whitelisted catalog**;
- any **function not permitted by the function whitelist** (see below);
- empty or unparsable input.

The guard **fails closed**: any unexpected error during validation results in a
`GuardException`, never an approved statement.

## The function whitelist

The whitelist is the control for which functions a query may call. It is a
`List<String>` of **full-match, case-sensitive regular expressions** (evaluated with
`java.util.regex.Pattern.matches`). Every function name appearing anywhere in the
query — SELECT list, `WHERE`, `HAVING`, subqueries, and so on — must be matched in
full by at least one pattern, or the whole statement is rejected with
`GuardException("Query has disallowed functions.")`.

```java
// allow three aggregate functions, nothing else
SqlGuard guard = factory.create("", "sch", catalog,
        List.of("sum", "count", "avg"), dialect);
```

Key points:

- **An empty list rejects every function** — `List.of()` is the secure default, and
  the guard is safe out of the box.
- Matching is **case-sensitive and anchored**: `"sum"` matches only `sum`, not `SUM`.
  Use `"(?i)sum"`, or list both spellings, when you need case-insensitivity.
- `List.of(".*")` allows **every** function and is strongly discouraged — see below.

## Keep the whitelist tight

Allow only the specific aggregate/scalar functions your use case needs. In particular,
**never** allow function families that have side effects or enable denial of service or
data exfiltration, for example:

| Category | Examples |
|---|---|
| Sequence writes | `nextval`, `setval` |
| Denial of service | `pg_sleep`, `benchmark` |
| File / network I/O, RCE | `dblink`, `lo_import`, `lo_export`, `pg_read_file`, `pg_ls_dir`, `xp_cmdshell`, `load_file` |
| Backend control | `pg_terminate_backend`, `pg_cancel_backend` |

A broad pattern such as `.*` re-enables all of these at once. Prefer an explicit,
enumerated list over any catch-all.

## Why there is no built-in denylist

The guard does **not** ship a hardcoded list of "dangerous" functions to block. Such a
denylist is:

- **inherently incomplete** — the set of side-effecting functions differs by database,
  extension, and version, and grows over time;
- a source of **false confidence** — a denylist that looks comprehensive invites callers
  to relax the whitelist, which is exactly the wrong direction.

The whitelist is the authoritative control, and it is already default-deny. The correct
lever is keeping that whitelist tight, not maintaining a denylist that can never be
complete.

## Defense in depth (outside this library)

Treat the guard as one layer, not the only one. Execute guarded SQL under a **read-only
database role** and/or inside a **read-only transaction** (for example
`SET TRANSACTION READ ONLY`, or `default_transaction_read_only` on PostgreSQL). Then even
a future bypass — a construct no one anticipated — still cannot write. The parser-level
guard should never be the sole barrier between untrusted SQL and the database.
