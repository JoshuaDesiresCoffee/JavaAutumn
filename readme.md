# Java Autumn

---

Best-in-class Java web framework.
Similar to Go frameworks like Chi.

Features:
- JDBC-based SQLite handling
- HTML template parser
- Easy routing registration
- Server-side rendering (SSR)
- Annotated entities (`@Table`) plus `CrudHandler` for list/create/update/delete
- Fast, modifiable and flexible

---

## Structure

### /Implementation/handler/
Application handlers (one class per resource). Most extend `Autumn.handler.CrudHandler<T>` and reuse the helpers in `Autumn.handler.BaseHandler`.
Route methods take an `Autumn.handler.Exchange`:
```
public void list(Exchange exchange) throws IOException
```
Lambdas / method references work too: `router.GET("/roles", roles::list);`


### /Implementation/repository/
`@Table`-annotated entity classes (e.g. `Artist`, `Artwork`, `Rating`).
Foreign keys are object references (`Artwork.artist`); collections use `@OneToMany`.
All CRUD goes through `Autumn.orm.Db` (`Db.instance.SELECT.FROM(...)`, `INSERT.INTO(...)`, `UPDATE(...)`, `DELETE.FROM(...)`).

### /Autumn/
Framework code, layered:
- `Autumn.Router` - route registration and dispatch.
- `Autumn.handler.*` - `Exchange`, `BaseHandler`, `CrudHandler`.
- `Autumn.orm.*` - `Db`, query builders, `EntityMapper`, schema sync.
- `Autumn.templating.*` - `Templater`, `ObjectToMapConverter`, `Json`.

### /Autumn/templating/
Contains the template engine (`Templater`).

#### Templater
- Reads templates from `/Implementation/templates`.
- Renders placeholders with `Map<String, ?>` context values.
- Supported syntax:
```
{{ key }}            // value or nested key.path
{{#if key}} ... {{/if}}
{{#each items}} ... {{/each}}
```
Notes:
- Lists render comma-joined; objects use `displayedAs` / `name` if present (entity-friendly via duck-typing, no ORM import).
- Missing keys render as empty strings.
- Prevents `../` path traversal outside the template root.

Example usage:
```java
String html = Templater.render("index.html", Map.of(
    "title", "Hello from Java Autumn"
));
```

### /Implementation/templates/
Contains HTML files with templating structure.

### Implementation.App.java
Entrypoint: DB from `AppConfig` (`@Database`), schema sync, registers routes, starts the server.

---

## Endpoints

### SSR (server-rendered)
- `GET /`, `GET /users` — landing + user list.
- Detail pages: `GET /artist?id=…`, `/artwork`, `/provenance`, `/epoch`, `/user`, `/role`, `/rating` (rendered by `BaseHandler.renderDetail`).
- List + CRUD per resource:
    - Artworks: `GET /artworks`, `POST /artworks/create|update|delete`, `GET /artworks/edit?id=…`
    - Ratings: `GET /ratings`, `POST /ratings/create|delete`
    - Stars:   `GET /stars`,   `POST /stars/create|delete`
    - Roles:   `GET /roles`,   `POST /roles/create|delete|assign|remove-user`

### JSON API
- `GET /api/sidebar`
- `GET /api/user/all`, `POST /api/user_create|user_update|user_delete`
- `GET /api/artwork/all`

---

## Known limitations

- No global user switcher; user is picked per action.
- Roles modeled but not enforced yet.
- No CRUD UI for Artist, Epoch, Provenance.
- Rename folder names with package names (`handler` not `Handler`).

---

## Quick Start (Java 25, no Maven)

Prerequisites:
- Java 25 installed (`java -version`, `javac -version`)
- SQLite JDBC jar at `Autumn/lib/sqlite-jdbc-3.51.3.0.jar`

One script per platform; build is included.

### macOS / Linux (`dev.sh`)

```bash
chmod +x dev.sh           # once after clone

./dev.sh build            # compile only
./dev.sh build --clean    # wipe out/ first
./dev.sh run              # build + start server (Implementation.App)
./dev.sh run --skip-build
./dev.sh test             # TestRunner with -ea
./dev.sh seed             # seed if empty
./dev.sh seed --reset     # delete app.db, sync + seed
./dev.sh kill             # stop whatever listens on port 8080
```

First time after clone: `./dev.sh seed` once.

### Windows (`dev.ps1`)

```powershell
.\dev.ps1 build
.\dev.ps1 build -Clean
.\dev.ps1 run
.\dev.ps1 run -SkipBuild
.\dev.ps1 test
.\dev.ps1 seed
.\dev.ps1 seed -Reset
.\dev.ps1 kill
```

### Plain `java` (no script)

```bash
java -ea -cp "out:Autumn/lib/sqlite-jdbc-3.51.3.0.jar" Implementation.tests.TestRunner
```