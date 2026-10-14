# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

mdr2 ("Madras2") is a production management tool for DAISY Talking Books. It coordinates productions from order (ABACUS ERP / library) through recording (Obi), validation/encoding (DAISY Pipeline 1 & 2) to archiving in a legacy in-house archive. Luminus-based Clojure backend + ClojureScript (re-frame/reagent, Bulma) frontend.

## Commands

- Build uberjar (also runs `shadow-cljs release app`; needs `npm install` first): `lein uberjar` → `target/uberjar/mdr2-<version>-standalone.jar`
- Run jar: `java -Dconf=dev-config.edn -jar <jar>`
- DB migrations from CLI: `lein run migrate` / `lein run rollback` (REPL equivalents incl. `create-migration` and `reset-db` in `env/dev/clj/user.clj`)
- Tests (what CI runs): `lein test :non-database`
- All tests (needs DB from `test-config.edn`): `lein test`
- Single namespace / test: `lein test mdr2.handler-test` / `lein test :only mdr2.handler-test/test-app`
- Frontend dev: `npx shadow-cljs watch app` (nREPL on 7002); backend REPL via `lein repl` (init-ns `user`, use `(start)`/`(stop)`/`(restart)`)
- Lint: `lein kibit`, `lein eastwood`. API docs: `lein codox`
- Release: `lein release` (bumps version, tags, pushes; a pushed `X.Y.Z` tag triggers the GitHub release workflow)

Tests tagged `^:database` require a live MySQL; keep them tagged so CI's `:non-database` selector skips them.

## Config

`cprop` loads config from the file given by `-Dconf=` (`dev-config.edn` in `:dev`, `test-config.edn` in `:test`; both git-ignored and contain credentials — never commit or echo them). Read via `mdr2.config/env`. Key settings: `:database-url`, `:archive-database-url`, `:production-path`, `:archive-*-spool-dir`, `:pipeline1-install-path`, `:pipeline2-*`, `:abacus-export-dir`, `:ldap-address`, `:jwt-secret`.

## Architecture

- **Lifecycle**: everything stateful is a `mount` `defstate` (config, two DB pools, LDAP pool, HTTP server, nREPL, core.async queues and their consumers). `mdr2.core/-main` handles `init`/migrations or starts the app.
- **Two databases** (`mdr2.db.core`): `*db*` (madras2, queries in `resources/sql/queries.sql`) and `*archive-db*` (legacy archive "agadir", `resources/sql/archive-queries.sql`). Both via HugSQL/conman; functions are generated from the `.sql` files, so add queries there, not in Clojure. Migrations in `resources/migrations` (madras2 DB only).
- **Production state machine** (`mdr2.production`, valid states in `mdr2.production.spec`): `new → structured → recorded → (pending-split → split) → encoded → cataloged → archived`, plus `failed`, `repairing`, `deleted`. Change state via the `set-state-*!` functions, which also handle side effects (dirs, dates, ABACUS notification). On-disk layout per production is in `mdr2.production.path`.
- **Async work**: long-running jobs go through core.async channels in `mdr2.queues` (`encode`, `archive`, `repair`, `notify-abacus`). Consumers live in `mdr2.<x>.async` and are pulled in by `mdr2.queue-consumers` — split out to avoid a `production → queues → production` circular dependency. Domain logic is in the matching `mdr2.<x>.core`.
- **Integrations**: `abacus.*` (file-based import/export with the ERP), `obi`, `pipeline1` (shells out to local install), `pipeline2.*` (REST client), `dtbook`/`dtb`/`schema_validation` (XML/DTB handling, Jing RelaxNG validation; schemas in `resources/schema`), `vubis` (library catalog import), `archive.core` (spool dirs + archive DB), `ldap`/`auth` (LDAP login, JWT via buddy).
- **HTTP**: reitit. `routes/home.clj` serves the SPA shell; `routes/services.clj` is the REST API (swagger, specs from `production.spec`). Example requests in `test/rest-api.http`.
- **Frontend**: `src/cljs/mdr2`, entry `mdr2.app` (in `env/{dev,prod}/cljs`), views per production list under `productions/`. Shared code (i18n via tempura, validation) in `src/cljc`.
