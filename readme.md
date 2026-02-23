# Java Autumn

---

Best-in-class Java web framework.
Similar to Go frameworks like Chi.

Features:
- Data Base handling
- HTML template parser
- Easy routing registration
- Dynamic routes
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

### /Service/
Different kind of services needed for the framework.
- Router: Handles registration and execution of routing within the web framework.
- Templater: Reads files, renders templates and provides interfaces.
- DataBasePooler: Serves as a database connection and connection pooler.

### /templates/
Contains HTML files with templating structure.

### App.java
Entrypoint of the application. Reads configuration files, creates initial objects and registers routes, as well as starts the webserver.