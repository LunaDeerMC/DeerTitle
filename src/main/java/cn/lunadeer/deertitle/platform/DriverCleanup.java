package cn.lunadeer.deertitle.platform;

import java.io.IOException;
import java.net.URLClassLoader;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Release this plugin's JDBC resources, including its private Lite library loader. */
public final class DriverCleanup {
    private DriverCleanup() {}
    public static void release(ClassLoader owner, ClassLoader dependencyLoader, boolean mysqlUsed) {
        // Paper gives each plugin its own libraries loader, but parent-first loading can
        // also resolve Hikari/JDBC from the server. Those shared loaders must stay alive.
        ClassLoader privateLibraries = privateLibraryLoader(owner, dependencyLoader);
        try {
            var drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                var driver = drivers.nextElement();
                if (!owned(driver.getClass().getClassLoader(), owner, privateLibraries)) continue;
                // JDBC service discovery can load MySQL even when another database is configured.
                if (driver.getClass().getName().startsWith("com.mysql.")) mysqlUsed = true;
                try { DriverManager.deregisterDriver(driver); }
                catch (SQLException ignored) { /* Pool resources were already closed independently. */ }
            }
            if (mysqlUsed) {
                try {
                    Class<?> cleanup = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", false, owner);
                    if (owned(cleanup.getClassLoader(), owner, privateLibraries))
                        cleanup.getMethod("uncheckedShutdown").invoke(null);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Never shut down another plugin's shared driver or cleanup thread.
                }
            }
        } finally {
            // Bukkit closes the plugin loader itself, but not its separate libraries loader.
            if (privateLibraries instanceof URLClassLoader libraries) {
                try { libraries.close(); }
                catch (IOException ignored) { /* JDBC resources have already been released. */ }
            }
        }
    }

    private static boolean owned(ClassLoader loader, ClassLoader owner, ClassLoader privateLibraries) {
        return loader == owner || (privateLibraries != null && loader == privateLibraries);
    }

    private static ClassLoader privateLibraryLoader(ClassLoader owner, ClassLoader dependencyLoader) {
        if (dependencyLoader == null || dependencyLoader == owner) return null;
        for (ClassLoader parent = owner.getParent(); parent != null; parent = parent.getParent())
            if (dependencyLoader == parent) return null;
        return dependencyLoader;
    }
}
