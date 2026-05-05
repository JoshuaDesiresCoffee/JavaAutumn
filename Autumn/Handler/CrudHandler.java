package Autumn.handler;

import Autumn.Router;
import Autumn.orm.Db;
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

/**
 * Generic base class for CRUD-style HTTP handlers.
 *
 * <p>Subclasses get list/create/update/delete/api/detail operations for free.
 * Override the protected hooks ({@link #decorateRow}, {@link #extraListContext},
 * {@link #bindFromForm}, {@link #validate}) to customize behavior.
 *
 * <p>Stateless helpers live on {@link BaseHandler} so handlers that don't fit
 * the CRUD shape can use them without inheriting from this class.
 */
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
            List<T> entities = Db.instance.SELECT.FROM(entityClass).EXEC();
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
            Db.instance.INSERT(entity).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            exchange.send(500, "Failed to create " + entityClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    public void update(Exchange exchange) throws IOException {
        try {
            Optional<Integer> idOpt = BaseHandler.idParam(exchange);
            if (idOpt.isEmpty()) {
                exchange.send(400, "id is required");
                return;
            }
            T entity = bindFromForm(exchange, true);
            String error = validate(entity);
            if (error != null) {
                exchange.send(400, error);
                return;
            }
            Db.instance.UPDATE(entity).BY_ID(idOpt.get()).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            exchange.send(500, "Failed to update " + entityClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    public void delete(Exchange exchange) throws IOException {
        Optional<Integer> idOpt = BaseHandler.idParam(exchange);
        if (idOpt.isEmpty()) {
            exchange.send(400, "id is required");
            return;
        }
        try {
            Db.instance.DELETE.FROM(entityClass).BY_ID(idOpt.get()).EXEC();
            exchange.redirect(routePrefix);
        } catch (Exception e) {
            BaseHandler.renderDeleteError(exchange, e);
        }
    }

    public void api(Exchange exchange) throws IOException {
        try {
            exchange.json(Json.toJson(Db.instance.SELECT.FROM(entityClass).EXEC()));
        } catch (Exception e) {
            exchange.send(500, e.getMessage());
        }
    }

    public void detail(Exchange exchange) throws IOException {
        BaseHandler.renderDetail(exchange, entityClass.getSimpleName(), entityClass);
    }

    /** Register the standard CRUD routes (GET list, POST create/update/delete). */
    public void registerCrud(Router router) {
        router.GET(routePrefix, this::list);
        router.POST(routePrefix + "/create", this::create);
        router.POST(routePrefix + "/update", this::update);
        router.POST(routePrefix + "/delete", this::delete);
    }

    // ---------------------------------------------------------------------
    // Hooks for subclasses
    // ---------------------------------------------------------------------

    /** Add fields to a row before it is rendered. Default: no-op. */
    protected void decorateRow(T entity, Map<String, Object> row) {}

    /** Provide additional context entries for the list template. Default: empty. */
    protected Map<String, Object> extraListContext() {
        return Map.of();
    }

    /** Validate a bound entity. Return null on success, an error message on failure. */
    protected String validate(T entity) {
        return null;
    }

    /**
     * Build an entity from form parameters, matching field names. The {@code id} field
     * is set only when {@code includeId} is true (i.e. for updates).
     */
    protected T bindFromForm(Exchange exchange, boolean includeId) throws Exception {
        T entity = entityClass.getDeclaredConstructor().newInstance();
        for (Field f : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
            if (!includeId && "id".equals(f.getName())) continue;
            String raw = exchange.formParam(f.getName(), "");
            if (raw.isBlank()) continue;
            f.setAccessible(true);
            f.set(entity, BaseHandler.coerce(raw, f.getType()));
        }
        return entity;
    }
}
