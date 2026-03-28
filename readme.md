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

### /Handler/
Handler contains all classes which feature the necessary lambdas for routing registration.
In order to be eligable as a handler for routing, it has to follow the following footprint:
```
public static void MyHandlerName(HttpExchange exchange)
```
Returns will be ignored and only static functions allowed.


### /Repository/
Contains all classes modeling to- and querying the database.
`GenericTableRepository` introspects table metadata and can perform CRUD on user-defined tables.

### /Service/
Different kind of services needed for the framework.
- Router: Handles registration and execution of routing within the web framework.
- Templater: Reads files and renders templates.
- Database: JDBC connection provider.

### /Service/templating/
Contains the template engine (`Templater`).

#### Templater 
- Reads templates from `/templates`.
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

### /templates/
Contains HTML files with templating structure.

### App.java
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
- SQLite JDBC jar at `lib/sqlite-jdbc-3.51.3.0.jar`

Build:
```powershell
.\build.ps1
```

Run (build + start):
```powershell
.\run.ps1
```

Run without rebuilding:
```powershell
.\run.ps1 -SkipBuild
```

Optional: clean build output first:
```powershell
.\build.ps1 -Clean
```

### Tests (no extra libraries)

Uses Java’s built-in `assert` only; the JVM must enable assertions (`-ea`). Test sources live under `/tests/` and compile with the app.

```powershell
.\test.ps1
```

Skip rebuild:

```powershell
.\test.ps1 -SkipBuild
```

Equivalent manual command after `.\build.ps1`:

```powershell
java -ea -cp "out;lib/sqlite-jdbc-3.51.3.0.jar" tests.TestRunner
```
