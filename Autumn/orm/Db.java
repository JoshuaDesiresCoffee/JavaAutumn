package Autumn.orm;

import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

public class Db {

    public static Db instance;

    private final String url;
    private final String user;
    private final String password;

    public final Select SELECT = new Select();

    private Db(String url, String user, String password) {
        this.url      = url;
        this.user     = user;
        this.password = password;
    }

    public static void init() {
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.isDirectory()) scanForDatabase(f, f);
        }
        if (instance == null) throw new RuntimeException("No @Database annotated class found.");
    }

    private static void scanForDatabase(File root, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) scanForDatabase(root, f);
            else if (f.getName().endsWith(".class")) {
                String name = root.toURI().relativize(f.toURI()).getPath()
                        .replace('/', '.').replace(".class", "");
                try {
                    Class<?> c = Class.forName(name);
                    if (c.isAnnotationPresent(Database.class)) {
                        Database ann = c.getAnnotation(Database.class);
                        ensureDirectories(ann.url());
                        instance = new Db(ann.url(), ann.user(), ann.password());
                        instance.sync(findTableClasses().toArray(new Class[0]));
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void ensureDirectories(String url) {
        if (url.startsWith("jdbc:sqlite:")) {
            String path = url.substring("jdbc:sqlite:".length());
            File parent = new File(path).getParentFile();
            if (parent != null) parent.mkdirs();
        }
    }

    private static List<Class<?>> findTableClasses() {
        List<Class<?>> found = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File f = new File(entry);
            if (f.isDirectory()) scanForTables(f, f, found);
        }
        return found;
    }

    private static void scanForTables(File root, File dir, List<Class<?>> found) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) scanForTables(root, f, found);
            else if (f.getName().endsWith(".class")) {
                String name = root.toURI().relativize(f.toURI()).getPath()
                        .replace('/', '.').replace(".class", "");
                try {
                    Class<?> c = Class.forName(name);
                    if (c.isAnnotationPresent(Table.class)) found.add(c);
                } catch (Throwable ignored) {}
            }
        }
    }

    public void sync(Class<?>... tables) {
        for (Class<?> t : tables) {
            if (!t.isAnnotationPresent(Table.class))
                throw new RuntimeException(t.getName() + " must be annotated with @Table");

            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(tableName(t)).append(" (");

            Field[] fields = t.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                sql.append(fields[i].getName()).append(" ").append(sqlType(fields[i].getType()));
                if (i < fields.length - 1) sql.append(", ");
            }
            sql.append(")");

            try (Connection conn = getConnection();
                 Statement stmt  = conn.createStatement()) {
                stmt.execute(sql.toString());
            } catch (SQLException e) {
                throw new RuntimeException("sync failed for " + t.getSimpleName(), e);
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static String tableName(Class<?> t) {
        Table ann = t.getAnnotation(Table.class);
        return ann.name().isEmpty() ? t.getSimpleName().toLowerCase() : ann.name();
    }

    private static String sqlType(Class<?> type) {
        if (type == int.class     || type == Integer.class) return "INTEGER";
        if (type == long.class    || type == Long.class)    return "BIGINT";
        if (type == double.class  || type == Double.class)  return "DOUBLE";
        if (type == boolean.class || type == Boolean.class) return "BOOLEAN";
        return "TEXT";
    }
    public <T> Query<T> INSERT(T obj) {
        return new Query<T>(this, Query.Mode.INSERT).withObject(obj);
    }

    public <T> Query<T> UPDATE(T obj) {
        return new Query<T>(this, Query.Mode.UPDATE).withObject(obj);
    }

    public final Delete DELETE = new Delete();

    public class Delete {
        public <T> Query<T> FROM(Class<T> tableClass) {
            return new Query<T>(Db.this, Query.Mode.DELETE).FROM(tableClass);
        }
    }

    public class Select {
        public <T> Query<T> FROM(Class<T> tableClass) {
            return new Query<T>(Db.this, Query.Mode.SELECT).FROM(tableClass);
        }
    }
}