package cn.lunadeer.deertitle.platform;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DriverCleanupTest {
    @TempDir Path directory;

    @Test void liteReleasesPrivateDriversAndLibraryFilesButPreservesServerDrivers() throws Exception {
        try (var environment = new Environment(directory, "library")) {
            environment.register(SharedDriver.class);
            environment.register(LibraryDriver.class);
            environment.register(PluginDriver.class);
            environment.loadMysql();
            assertEquals(4, environment.drivers().size());
            assertTrue(environment.mysqlAlive());

            // mysqlUsed=false also covers discovery by JDBC while using another database.
            environment.release(environment.libraries, false);

            assertEquals(List.of(SharedDriver.class.getName()), environment.drivers());
            environment.assertMysqlStopped();
            assertTrue(environment.libraries.closed);
            assertFalse(environment.plugin.closed);
            assertFalse(environment.server.closed);
        }
    }

    @Test void fullReleasesBundledDriversWithoutClosingItsPluginLoader() throws Exception {
        try (var environment = new Environment(directory, "plugin")) {
            environment.register(SharedDriver.class);
            environment.register(PluginDriver.class);
            environment.loadMysql();

            environment.release(environment.plugin, true);

            assertEquals(List.of(SharedDriver.class.getName()), environment.drivers());
            environment.assertMysqlStopped();
            assertFalse(environment.plugin.closed);
            assertFalse(environment.server.closed);
        }
    }

    @Test void serverDependencyLoaderKeepsSharedDriversAndMysqlThreadAlive() throws Exception {
        try (var environment = new Environment(directory, "server")) {
            environment.register(SharedDriver.class);
            environment.register(PluginDriver.class);
            environment.loadMysql();

            environment.release(environment.server, true);

            assertEquals(List.of(SharedDriver.class.getName(), "com.mysql.cj.jdbc.Driver"), environment.drivers());
            assertTrue(environment.mysqlAlive());
            assertFalse(environment.server.closed);
            assertFalse(environment.plugin.closed);
            assertFalse(environment.libraries.closed);
        }
    }

    @Test void liteKeepsParentMysqlWhileRemovingItsOwnOtherDrivers() throws Exception {
        try (var environment = new Environment(directory, "server")) {
            environment.register(SharedDriver.class);
            environment.register(LibraryDriver.class);
            environment.loadMysql();

            environment.release(environment.libraries, true);

            assertEquals(List.of(SharedDriver.class.getName(), "com.mysql.cj.jdbc.Driver"), environment.drivers());
            assertTrue(environment.mysqlAlive());
            assertTrue(environment.libraries.closed);
            assertFalse(environment.server.closed);
        }
    }

    /** Same parent-first plugin/server/library topology as Bukkit's PluginClassLoader. */
    private static final class Environment implements AutoCloseable {
        private final TrackingLoader server;
        private final TrackingLoader libraries;
        private final TrackingLoader plugin;
        private final Class<?> probe;

        Environment(Path directory, String mysqlLocation) throws Exception {
            Path serverDirectory = directory.resolve("server");
            Path libraryDirectory = directory.resolve("libraries");
            Path pluginDirectory = directory.resolve("plugin");
            copyClasses(serverDirectory, FixtureDriver.class, SharedDriver.class);
            copyClasses(libraryDirectory, LibraryDriver.class);
            copyClasses(pluginDirectory, Probe.class, PluginDriver.class, DriverCleanup.class);
            URL mysql = com.mysql.cj.jdbc.Driver.class.getProtectionDomain().getCodeSource().getLocation();
            server = new TrackingLoader(urls(serverDirectory, mysqlLocation.equals("server") ? mysql : null),
                    ClassLoader.getPlatformClassLoader());
            libraries = new TrackingLoader(urls(libraryDirectory, mysqlLocation.equals("library") ? mysql : null), server);
            plugin = new TrackingLoader(urls(pluginDirectory, mysqlLocation.equals("plugin") ? mysql : null), server) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    try { return super.loadClass(name, resolve); }
                    catch (ClassNotFoundException ignored) { return libraries.loadClass(name); }
                }
            };
            probe = plugin.loadClass(Probe.class.getName());
        }

        void register(Class<?> fixture) throws Exception {
            probe.getMethod("register", String.class).invoke(null, fixture.getName());
        }

        void loadMysql() throws Exception { probe.getMethod("loadMysql").invoke(null); }

        @SuppressWarnings("unchecked")
        List<String> drivers() throws Exception { return (List<String>) probe.getMethod("drivers").invoke(null); }

        boolean mysqlAlive() throws Exception { return (boolean) probe.getMethod("mysqlAlive").invoke(null); }

        void release(ClassLoader dependencyLoader, boolean mysqlUsed) throws Exception {
            probe.getMethod("release", ClassLoader.class, boolean.class).invoke(null, dependencyLoader, mysqlUsed);
        }

        void assertMysqlStopped() throws Exception {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (mysqlAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(mysqlAlive());
        }

        @Override public void close() throws Exception {
            try { probe.getMethod("clear").invoke(null); }
            finally {
                plugin.close();
                libraries.close();
                server.close();
            }
        }

        private static URL[] urls(Path directory, URL mysql) throws IOException {
            URL classes = directory.toUri().toURL();
            return mysql == null ? new URL[]{classes} : new URL[]{classes, mysql};
        }

        private static void copyClasses(Path directory, Class<?>... classes) throws IOException {
            for (Class<?> type : classes) {
                String resource = type.getName().replace('.', '/') + ".class";
                Path target = directory.resolve(resource);
                Files.createDirectories(target.getParent());
                try (var input = type.getClassLoader().getResourceAsStream(resource)) {
                    if (input == null) throw new IOException("Missing fixture class " + resource);
                    Files.copy(input, target);
                }
            }
        }
    }

    private static class TrackingLoader extends URLClassLoader {
        boolean closed;
        TrackingLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    // Public fixtures are copied into isolated loaders. The probe must call DriverManager
    // from the plugin loader because JDBC filters registrations by the caller's visibility.
    public static class FixtureDriver implements Driver {
        public Connection connect(String url, Properties info) { return null; }
        public boolean acceptsURL(String url) { return false; }
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }

    public static final class SharedDriver extends FixtureDriver {}
    public static final class LibraryDriver extends FixtureDriver {}
    public static final class PluginDriver extends FixtureDriver {}

    public static final class Probe {
        public static void register(String name) throws Exception {
            DriverManager.registerDriver((Driver) Class.forName(name).getConstructor().newInstance());
        }
        public static List<String> drivers() {
            var result = new ArrayList<String>();
            var drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) result.add(drivers.nextElement().getClass().getName());
            return result;
        }
        public static void loadMysql() throws ClassNotFoundException { Class.forName("com.mysql.cj.jdbc.Driver"); }
        public static boolean mysqlAlive() throws Exception {
            return (boolean) mysqlCleanup().getMethod("isAlive").invoke(null);
        }
        private static Class<?> mysqlCleanup() throws ClassNotFoundException {
            return Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
        }
        public static void release(ClassLoader dependencyLoader, boolean mysqlUsed) {
            DriverCleanup.release(Probe.class.getClassLoader(), dependencyLoader, mysqlUsed);
        }
        public static void clear() throws Exception {
            var drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) DriverManager.deregisterDriver(drivers.nextElement());
            mysqlCleanup().getMethod("uncheckedShutdown").invoke(null);
        }
    }
}
