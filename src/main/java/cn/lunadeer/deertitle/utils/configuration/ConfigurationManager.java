package cn.lunadeer.deertitle.utils.configuration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigurationManager {

    private ConfigurationManager() {
    }

    public static ConfigurationFile load(Class<? extends ConfigurationFile> clazz, File file) throws Exception {
        if (!file.exists()) {
            return saveDefault(clazz, file);
        }
        ConfigurationFile instance = readConfigurationFile(YamlConfiguration.loadConfiguration(file), clazz);
        instance.save(file);
        return instance;
    }

    public static ConfigurationFile load(Class<? extends ConfigurationFile> clazz, File file, String versionFieldName) throws Exception {
        Field versionField = clazz.getField(versionFieldName);
        int currentVersion = versionField.getInt(null);
        ConfigurationFile instance = load(clazz, file);
        if (versionField.getInt(null) != currentVersion) {
            File backup = new File(file.getParentFile(), file.getName() + ".bak");
            if (backup.exists() && !backup.delete()) {
                throw new Exception("Failed to delete the backup configuration file.");
            }
            if (!file.renameTo(backup)) {
                throw new Exception("Failed to backup the configuration file.");
            }
            clazz.getField(versionFieldName).set(null, currentVersion);
            return saveDefault(clazz, file);
        }
        return instance;
    }

    public static ConfigurationFile saveDefault(Class<? extends ConfigurationFile> clazz, File file) throws Exception {
        createIfNotExist(file);
        YamlConfiguration yaml = writeConfigurationFile(clazz);
        yaml.options().width(250);
        yaml.save(file);
        return load(clazz, file);
    }

    private static YamlConfiguration writeConfigurationFile(Class<? extends ConfigurationFile> clazz) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        if (clazz.isAnnotationPresent(Headers.class)) {
            yaml.options().setHeader(List.of(clazz.getAnnotation(Headers.class).value()));
        }
        ConfigurationFile instance = clazz.getConstructor().newInstance();
        writeConfigurationPart(yaml, instance, null);
        return yaml;
    }

    public static void writeConfigurationPart(ConfigurationSection yaml, ConfigurationPart obj, String prefix) throws Exception {
        writeConfigurationPart(yaml, obj, prefix, false);
    }

    public static void writeConfigurationPart(ConfigurationSection yaml, ConfigurationPart obj, String prefix, boolean ignoreComment) throws Exception {
        for (Field field : obj.getClass().getFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(HandleManually.class)) {
                continue;
            }
            String newKey = camelToKebab(field.getName());
            if (prefix != null && !prefix.isEmpty()) {
                newKey = prefix + "." + newKey;
            }
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                writeConfigurationPart(yaml, (ConfigurationPart) field.get(obj), newKey);
            } else {
                yaml.set(newKey, field.get(obj));
            }
            if (!ignoreComment) {
                if (field.isAnnotationPresent(Comments.class)) {
                    yaml.setComments(newKey, List.of(field.getAnnotation(Comments.class).value()));
                }
                if (field.isAnnotationPresent(Comment.class)) {
                    yaml.setInlineComments(newKey, List.of(field.getAnnotation(Comment.class).value()));
                }
            }
        }
    }

    private static void createIfNotExist(File file) throws Exception {
        if (file.exists()) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("Failed to create " + parent.getAbsolutePath() + " directory.");
        }
        if (!file.createNewFile()) {
            throw new Exception("Failed to create " + file.getAbsolutePath() + " file.");
        }
    }

    private static ConfigurationFile readConfigurationFile(YamlConfiguration yaml, Class<? extends ConfigurationFile> clazz) throws Exception {
        ConfigurationFile instance = clazz.getConstructor().newInstance();
        instance.setYaml(yaml);
        PrePostProcessInOrder processes = getAndSortPrePostProcess(clazz);
        for (Method method : processes.preProcessMethods) {
            method.invoke(instance);
        }
        readConfigurationPart(instance.getYaml(), instance, null);
        for (Method method : processes.postProcessMethods) {
            method.invoke(instance);
        }
        return instance;
    }

    public static ConfigurationPart readConfigurationPart(ConfigurationSection yaml, ConfigurationPart obj, String prefix) throws Exception {
        for (Field field : obj.getClass().getFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(HandleManually.class)) {
                continue;
            }
            String key = camelToKebab(field.getName());
            if (prefix != null && !prefix.isEmpty()) {
                key = prefix + "." + key;
            }
            boolean missingKey = !yaml.contains(key);
            if (missingKey) {
                yaml.createSection(key);
                if (field.isAnnotationPresent(Comments.class)) {
                    yaml.setComments(key, List.of(field.getAnnotation(Comments.class).value()));
                }
                if (field.isAnnotationPresent(Comment.class)) {
                    yaml.setInlineComments(key, List.of(field.getAnnotation(Comment.class).value()));
                }
            }
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                field.set(obj, readConfigurationPart(yaml, (ConfigurationPart) field.get(obj), key));
            } else if (missingKey) {
                yaml.set(key, field.get(obj));
            } else {
                field.set(obj, yaml.get(key));
            }
        }
        return obj;
    }

    private static String camelToKebab(String camel) {
        return camel.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }

    private static PrePostProcessInOrder getAndSortPrePostProcess(Class<? extends ConfigurationFile> clazz) {
        Map<Method, Integer> preProcessMethodsWithPriority = new HashMap<>();
        Map<Method, Integer> postProcessMethodsWithPriority = new HashMap<>();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(PreProcess.class)) {
                preProcessMethodsWithPriority.put(method, method.getAnnotation(PreProcess.class).priority());
            }
            if (method.isAnnotationPresent(PostProcess.class)) {
                postProcessMethodsWithPriority.put(method, method.getAnnotation(PostProcess.class).priority());
            }
        }
        PrePostProcessInOrder result = new PrePostProcessInOrder();
        result.preProcessMethods = new ArrayList<>(preProcessMethodsWithPriority.keySet());
        result.preProcessMethods.sort(Comparator.comparingInt(preProcessMethodsWithPriority::get));
        result.postProcessMethods = new ArrayList<>(postProcessMethodsWithPriority.keySet());
        result.postProcessMethods.sort(Comparator.comparingInt(postProcessMethodsWithPriority::get));
        return result;
    }

    private static final class PrePostProcessInOrder {
        private List<Method> preProcessMethods = new ArrayList<>();
        private List<Method> postProcessMethods = new ArrayList<>();
    }
}
