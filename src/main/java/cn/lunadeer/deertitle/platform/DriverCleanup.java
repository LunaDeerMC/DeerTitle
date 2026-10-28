package cn.lunadeer.deertitle.platform;

import java.sql.DriverManager;
import java.sql.SQLException;

/** Release only JDBC registrations owned by this plugin's loader. */
public final class DriverCleanup {
    private DriverCleanup() {}
    public static void release(ClassLoader owner, boolean mysqlUsed) {
        var drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            var driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() != owner) continue;
            // JDBC service discovery can load MySQL even when another database is configured.
            if (driver.getClass().getName().startsWith("com.mysql.")) mysqlUsed = true;
            try { DriverManager.deregisterDriver(driver); }
            catch (SQLException ignored) { /* Pool resources were already closed independently. */ }
        }
        if (mysqlUsed) {
            try {
                Class<?> cleanup = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread", false, owner);
                if (cleanup.getClassLoader() == owner) cleanup.getMethod("uncheckedShutdown").invoke(null);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Never shut down another plugin's shared driver or cleanup thread.
            }
        }
    }
}
