package Autumn.handler;

import Autumn.Router;
import Autumn.orm.Db;
import Autumn.orm.Table;
import Autumn.templating.Json;
import Autumn.templating.ObjectToMapConverter;
import Autumn.templating.Templater;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generic CRUD handler. Subclasses get list/create/update/delete/api/detail and override the protected hooks. */
public abstract class CrudHandler<T> {

    protected final Class<T> entityClass;
    protected final String routePrefix;
    protected final String listTemplate;
    protected final String listKey;

    protected CrudHandler(Class<T> entityClass, String routePrefix, String listTemplate, String listKey) {
        this.entityClass = entityClass;
        this.routePrefix = routePrefix;
        this.listTemplate = listTemplate;
        this.listKey = listKey;
    }

    public void list(Exchange exchange) throws IOException {
        try {
            List<T> entities = selectAll();
            List<Map<String, Object>> rows = new ArrayList<>(entities.size());
            for (T entity : entities) {
                Map<String, Object> row = ObjectToMapConverter.convert(entity);
                if (row == null) continue;
                decorateRow(entity, row);
                rows.add(row);
            }
            Map<String, Object> ctx = new HashMap<>();
            ctx.put(listKey, rows);
            ctx.putAll(extraListContext());
            exchange.html(Templater.render(listTemplate, ctx));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public void create(Exchange exchange) throws IOException {
        try {
            T entity = bindFromForm(exchange, false);
            String error = validate(entity);
            if (error != null) {
                exchange.send(400, error);
                return;
            }
            Db.instance.INSERT.INTO(entityClass).VALUES(entity).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            exchange.send(500, "Failed to create " + entityClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    public void update(Exchange exchange) throws IOException {
        try {
            Integer id = requireId(exchange);
            if (id == null) return;
            T entity = bindFromForm(exchange, true);
            String error = validate(entity);
            if (error != null) {
                exchange.send(400, error);
                return;
            }
            Db.instance.UPDATE(entityClass).SET(entity).WHERE("id = ?", id).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            exchange.send(500, "Failed to update " + entityClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    public void delete(Exchange exchange) throws IOException {
        Integer id = requireId(exchange);
        if (id == null) return;
        try {
            Db.instance.DELETE.FROM(entityClass).WHERE("id = ?", id).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            BaseHandler.renderDeleteError(exchange, e);
        }
    }

    public void api(Exchange exchange) throws IOException {
        try {
            exchange.json(Json.toJson(selectAll()));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public void detail(Exchange exchange) throws IOException {
        BaseHandler.renderDetail(exchange, entityClass.getSimpleName(), entityClass);
    }

    /** Registers list (GET) and create/update/delete (POST) on the route prefix. */
    public void registerCrud(Router router) {
        router.GET(routePrefix, this::list);
        router.POST(routePrefix + "/create", this::create);
        router.POST(routePrefix + "/update", this::update);
        router.POST(routePrefix + "/delete", this::delete);
    }

    /** Reads the id from the request; sends 400 and returns null if missing. */
    private static Integer requireId(Exchange exchange) throws IOException {
        Optional<Integer> id = BaseHandler.idParam(exchange);
        if (id.isEmpty()) {
            exchange.send(400, "id is required");
            return null;
        }
        return id.get();
    }

    /** Override to add JOIN(...) or filtering. Default: a flat SELECT with id-only FK stubs. */
    protected List<T> selectAll() {
        return Db.instance.SELECT.FROM(entityClass).EXEC();
    }

    protected void decorateRow(T entity, Map<String, Object> row) {}

    protected Map<String, Object> extraListContext() {
        return Map.of();
    }

    /** Returns null if valid, otherwise an error message shown as 400. */
    protected String validate(T entity) {
        return null;
    }

    /** Build entity from form params. FK fields are read as "{@code <name>Id}" and stored as id-only stubs. */
    protected T bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        T entity = entityClass.getDeclaredConstructor().newInstance();
        for (Field f : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            if (!includeId && "id".equals(f.getName())) continue;
            f.setAccessible(true);

            Class<?> ftype = f.getType();
            if (ftype.isAnnotationPresent(Table.class)) {
                String raw = exchange.formParam(f.getName() + "Id", "");
                if (raw.isBlank()) continue;
                f.set(entity, BaseHandler.stubWithId(ftype, Integer.parseInt(raw.trim())));
                continue;
            }

            String raw = exchange.formParam(f.getName(), "");
            if (raw.isBlank()) continue;
            f.set(entity, BaseHandler.coerce(raw, ftype));
        }
        return entity;
    }
}
