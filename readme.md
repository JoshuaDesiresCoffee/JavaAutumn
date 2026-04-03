# Java Autumn 

Best-in-class Java web framework.  
A lightweight and flexible alternative to heavy frameworks like Spring, inspired by minimal Go frameworks such as Chi.

Java Autumn provides routing, server-side rendering, and database access out of the box — with minimal setup and no magic.

---

## ✨ Features

### 🚀 Easy Routing Registration
Define routes using simple Java functions without annotations or reflection.

```java
router.handle("GET", "/", exchange -> IndexHandler.get(new Exchange(exchange)));

Supports all HTTP methods (GET, POST, PUT, DELETE)
Clean separation between routing and logic
Minimal boilerplate

## Simple Handler Model
Handlers are static Java methods with direct access to the request and response.

public class IndexHandler {
    public static void get(Exchange exchange) throws IOException {
        String html = Templater.render("index.html", null);
        exchange.sendHTML(200, html);
    }
}
No framework magic
Easy to understand and debug
Full control over HTTP handling
