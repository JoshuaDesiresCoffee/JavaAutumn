# Java Autumn

---

Best-in-class Java web framework.
Similar to Go frameworks like Chi.

Features:
- JDBC-based SQLite handling
- HTML template parser
- Easy routing registration
- Server-side rendering (SSR)
- Generic table CRUD via metadata
- Fast, modifyable and flexible

---

## Structure

### /Implementation.handler/
Implementation.handler contains all classes which feature the necessary lambdas for routing registration.
In order to be eligable as a handler for routing, it has to follow the following footprint:
```
Implementation.templates.public static void MyHandlerName(HttpExchange exchange)
```
Returns will be ignored and only static functions allowed.


### /Repository/
Contains all classes modeling to- and querying the database.
`GenericTableRepository` introspects table metadata and can perform CRUD on user-defined tables.

### /Autumn.Implementation.handler.Service/
Different kind of services needed for the framework.
- Router: Handles registration and execution of routing within the web framework.
- Templater: Reads files and renders Implementation.templates.
- Database: JDBC connection provider.

### /Autumn.Implementation.handler.Service/templating/
Contains the template engine (`Templater`).

#### Templater 
- Reads Implementation.templates from `/Implementation.templates`.
- Renders placeholders with `Map<String, ?>` context values.
- Supports placeholder syntax:
```
{{ key }}
```
Notes:
- Missing keys render as empty strings
- Prevents `../` path traversal outside the template root

Example usage:
```java
String html = Templater.render("index.html", Map.of(
    "title", "Hello from Java Autumn"
));
```

### /Implementation.templates/
Contains HTML files with templating structure.

### Implementation.App.java
Entrypoint of the application. Reads configuration files, creates initial objects and registers routes, as well as starts the webserver.

---

## Endpoints

### SSR
- `GET /` renders table selection + CRUD forms server-side.
- `POST /create?table=<name>`
- `POST /update?table=<name>`
- `POST /delete?table=<name>`

### API formula
- `GET /api/tables`
- `GET /api/rows?table=<name>`
- `POST /api/rows?table=<name>`
- `PUT /api/rows?table=<name>&id=<pk>`
- `DELETE /api/rows?table=<name>&id=<pk>`

---

## Quick Start (Java 25, no Maven)

Prerequisites:
- Java 25 installed (`java -version`, `javac -version`)
- SQLite JDBC jar at `Autumn.lib/sqlite-jdbc-3.51.3.0.jar`

Compile only:
```powershell
.\build.ps1
```

Optional: clean output first:
```powershell
.\build.ps1 -Clean
```

Everything else goes through **`dev.ps1`** (build first unless you pass `-SkipBuild`):

```powershell
.\dev.ps1 run              # server (Implementation.App)
.\dev.ps1 run -SkipBuild
.\dev.ps1 test             # asserts; JVM `-ea`
.\dev.ps1 test -SkipBuild
.\dev.ps1 seed             # demo data if DB is empty
.\dev.ps1 seed -Reset      # delete SQLite file, re-seed
.\dev.ps1 kill             # stop listener on port 8080
```

First time after clone: `.\dev.ps1 seed` once.

### macOS / Linux (bash)

Make scripts executable once:

```bash
chmod +x build.sh dev.sh
```

Compile:

```bash
./build.sh
./build.sh --clean
```

Same workflow as Windows, via **`dev.sh`**;

```bash
./dev.sh run
./dev.sh run --skip-build
./dev.sh test
./dev.sh seed
./dev.sh seed --reset
./dev.sh kill               
```

Or

```bash
java -ea -cp "out:Autumn/lib/sqlite-jdbc-3.51.3.0.jar" Implementation.tests.TestRunner
```