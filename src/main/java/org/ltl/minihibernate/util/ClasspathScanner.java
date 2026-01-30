package org.ltl.minihibernate.util;

import jakarta.persistence.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans classpath for classes annotated with @Entity.
 * 
 * <pre>
 * List<Class<?>> entities = ClasspathScanner.scan("org.myproject.entity");
 * // Returns all @Entity classes in that package
 * </pre>
 */
public class ClasspathScanner {

  private static final Logger log = LoggerFactory.getLogger(ClasspathScanner.class);

  /**
   * Scan a package for @Entity annotated classes.
   */
  public static List<Class<?>> scan(String packageName) {
    List<Class<?>> entityClasses = new ArrayList<>();
    String path = packageName.replace('.', '/');

    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      Enumeration<URL> resources = classLoader.getResources(path);

      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        String protocol = resource.getProtocol();

        if ("file".equals(protocol)) {
          scanDirectory(new File(resource.getFile()), packageName, entityClasses);
        } else if ("jar".equals(protocol)) {
          scanJar(resource, path, entityClasses);
        }
      }
    } catch (Exception e) {
      log.error("Failed to scan package: {}", packageName, e);
    }

    log.info("Found {} entity classes in package '{}'", entityClasses.size(), packageName);
    return entityClasses;
  }

  private static void scanDirectory(File directory, String packageName, List<Class<?>> result) {
    if (!directory.exists()) {
      return;
    }

    File[] files = directory.listFiles();
    if (files == null) {
      return;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        scanDirectory(file, packageName + "." + file.getName(), result);
      } else if (file.getName().endsWith(".class")) {
        String className = packageName + "." + file.getName().replace(".class", "");
        checkAndAddEntity(className, result);
      }
    }
  }

  private static void scanJar(URL resource, String path, List<Class<?>> result) {
    String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
    
    try (JarFile jar = new JarFile(jarPath)) {
      Enumeration<JarEntry> entries = jar.entries();
      
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        
        if (name.startsWith(path) && name.endsWith(".class")) {
          String className = name.replace('/', '.').replace(".class", "");
          checkAndAddEntity(className, result);
        }
      }
    } catch (IOException e) {
      log.error("Failed to scan JAR: {}", jarPath, e);
    }
  }

  private static void checkAndAddEntity(String className, List<Class<?>> result) {
    try {
      Class<?> clazz = Class.forName(className);
      if (clazz.isAnnotationPresent(Entity.class)) {
        result.add(clazz);
        log.debug("Found entity: {}", className);
      }
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      log.trace("Could not load class: {}", className);
    }
  }
}
