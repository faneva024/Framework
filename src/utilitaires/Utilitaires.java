package utilitaires;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import models.FileUpload;

public class Utilitaires {

    public static List<String> getClassByPackageAndAnnotation(Class<? extends Annotation> annotation,
            String packageName, ElementType type) throws Exception {
        if (typeElementValide(annotation, type)) {
            List<String> listeClass = new ArrayList<>();
            List<Class<?>> classInPackage = getClassByPackage(packageName);
            List<Class<?>> classByAnnotation = filterByAnnotation(classInPackage, annotation);

            for (Class<?> class1 : classByAnnotation) {
                listeClass.add(class1.getSimpleName());
            }

            return listeClass;
        }
        return null;
    }

    public static List<Class<?>> getClassByPackage(String packageName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new IllegalArgumentException("Aucun répertoire trouvé pour le package " + packageName);
        }

        File directory = new File(resource.getFile());
        List<Class<?>> classes = new ArrayList<>();

        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".class")) {
                        String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                        classes.add(Class.forName(className));
                    }
                }
            }
        }

        return classes;
    }

    public static List<Class<?>> filterByAnnotation(List<Class<?>> listeClass, Class<? extends Annotation> annotation) {
        List<Class<?>> classDispo = new ArrayList<>();
        for (Class<?> classes : listeClass) {
            if (classes.isAnnotationPresent(annotation)) {
                classDispo.add(classes);
            }
        }
        return classDispo;
    }

    public static boolean typeElementValide(Class<? extends Annotation> annotation, ElementType type) {
        Target target = annotation.getAnnotation(Target.class);
        ElementType[] elementTypes = target.value();

        for (ElementType elementType : elementTypes) {
            if (type.equals(elementType)) {
                return true;
            }
        }

        return false;
    }

    public static List<Method> getMethodesAnnotees(Class<?> classe, Class<? extends Annotation> annotation) {
        List<Method> listeMethodes = new ArrayList<>();
        for (Method methode : classe.getDeclaredMethods()) {
            if (methode.isAnnotationPresent(annotation)) {
                listeMethodes.add(methode);
            }
        }
        return listeMethodes;
    }

    public static boolean isBasicType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Integer.class || type == Double.class
                || type == Boolean.class || type == Float.class
                || type == Long.class || type == Short.class
                || type == Byte.class || type == Character.class
                || type == java.sql.Date.class
                || type == java.util.Date.class;
    }

    /**
     * Lit une propriété globale depuis le fichier app.properties s'il existe
     */
    public static String getProperty(String key) {
        try (InputStream input = Utilitaires.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (input == null) return null;
            Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty(key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convertit une Part Jakarta Servlet en objet FileUpload
     */
    public static FileUpload processFileUpload(Part part) throws Exception {
        if (part == null || part.getSubmittedFileName() == null || part.getSubmittedFileName().trim().isEmpty()) {
            return null;
        }
        String fileName = part.getSubmittedFileName();
        byte[] bytes;
        try (InputStream inputStream = part.getInputStream()) {
            bytes = inputStream.readAllBytes();
        }
        return new FileUpload(fileName, bytes);
    }

    /**
     * Hydrate un objet complexe à partir des paramètres HTTP et des fichiers
     */
    public static Object hydrateObject(Class<?> clazz, HttpServletRequest request) throws Exception {
        Object instance = clazz.getDeclaredConstructor().newInstance();
        boolean isMultipart = request.getContentType() != null && 
                              request.getContentType().toLowerCase().startsWith("multipart/form-data");

        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            if (fieldType == FileUpload.class) {
                // Attribut de type FileUpload dans un objet métier
                if (isMultipart) {
                    Part part = request.getPart(fieldName);
                    FileUpload fileUpload = processFileUpload(part);
                    invokeSetter(instance, clazz, fieldName, fieldType, fileUpload);
                }
            } else if (isBasicType(fieldType)) {
                String parameterValue = request.getParameter(fieldName);
                if (parameterValue != null && !parameterValue.trim().isEmpty()) {
                    Object convertedValue = castParameterValue(parameterValue, fieldType);
                    invokeSetter(instance, clazz, fieldName, fieldType, convertedValue);
                }
            } else {
                // Objets imbriqués (ex: departement.nom)
                String prefix = fieldName + ".";
                boolean hasNestedParams = false;

                Enumeration<String> paramNames = request.getParameterNames();
                while (paramNames.hasMoreElements()) {
                    if (paramNames.nextElement().startsWith(prefix)) {
                        hasNestedParams = true;
                        break;
                    }
                }

                if (hasNestedParams) {
                    Object nestedInstance = hydrateNestedObject(fieldType, prefix, request);
                    invokeSetter(instance, clazz, fieldName, fieldType, nestedInstance);
                }
            }
        }

        return instance;
    }

    private static Object hydrateNestedObject(Class<?> clazz, String prefix, HttpServletRequest request) throws Exception {
        Object instance = clazz.getDeclaredConstructor().newInstance();

        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            String fullParamName = prefix + fieldName;

            if (isBasicType(fieldType)) {
                String parameterValue = request.getParameter(fullParamName);
                if (parameterValue != null && !parameterValue.trim().isEmpty()) {
                    Object convertedValue = castParameterValue(parameterValue, fieldType);
                    invokeSetter(instance, clazz, fieldName, fieldType, convertedValue);
                }
            } else {
                String newPrefix = fullParamName + ".";
                Object nestedInstance = hydrateNestedObject(fieldType, newPrefix, request);
                invokeSetter(instance, clazz, fieldName, fieldType, nestedInstance);
            }
        }

        return instance;
    }

    private static void invokeSetter(Object targetInstance, Class<?> clazz, String fieldName, Class<?> paramType, Object value) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method setter = clazz.getMethod(setterName, paramType);
            setter.invoke(targetInstance, value);
        } catch (Exception e) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(targetInstance, value);
            } catch (Exception ex) {
                // Ignore si inaccessible
            }
        }
    }

    /**
     * Résolution des arguments pour l'exécution d'une méthode de contrôleur
     */
    public static Object[] resolveArguments(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        boolean isMultipart = request.getContentType() != null && 
                              request.getContentType().toLowerCase().startsWith("multipart/form-data");

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            if (paramType == FileUpload.class) {
                // Cas 1 : Le paramètre de la méthode est directement FileUpload
                if (isMultipart) {
                    Part part = request.getPart(param.getName());
                    args[i] = processFileUpload(part);
                }
            } else if (isBasicType(paramType)) {
                // Cas 2 : Types basiques (String, int...)
                String paramName = param.getName();
                String paramValue = request.getParameter(paramName);
                args[i] = castParameterValue(paramValue, paramType);
            } else {
                // Cas 3 : Objets complexes
                args[i] = hydrateObject(paramType, request);
            }
        }

        return args;
    }

    public static Object castParameterValue(String value, Class<?> type) {
        if (value == null || (value.trim().isEmpty() && type != String.class)) {
            if (type == int.class || type == double.class || type == float.class || type == long.class) return 0;
            if (type == boolean.class) return false;
            return null;
        }

        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == java.sql.Date.class) return java.sql.Date.valueOf(value);

        return value;
    }
}