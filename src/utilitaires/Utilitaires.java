package utilitaires;

import java.io.File;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Part;
import models.FileUpload;
import java.io.InputStream;

import models.Session;

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

        if (!"file".equals(resource.getProtocol())) {
            throw new IllegalArgumentException("Le package " + packageName
                    + " doit être un dossier (WEB-INF/classes) et non un JAR.");
        }
        // toURI() décode correctement les espaces et caractères spéciaux du chemin
        File directory = new File(resource.toURI());
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

    /**
     * Vérifie si le type fourni est un type simple/primitif ou assimilé.
     */
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
     * Hydrate un objet racine à partir des paramètres de la requête HTTP.
     */
    public static Object hydrateObject(Class<?> clazz, HttpServletRequest request) throws Exception {
        Object instance = clazz.getDeclaredConstructor().newInstance();

        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            if (field.getType() == FileUpload.class) {
                if (request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/form-data")) {
                    Part part = request.getPart(fieldName);
                    FileUpload fileUpload = processFileUpload(part);
                    invokeSetter(instance, clazz, fieldName, field.getType(), fileUpload);
                }
            } else if (isBasicType(fieldType)) {
                // Attribut simple de l'objet racine (ex: nom, age, dateNaissance)
                String parameterValue = request.getParameter(fieldName);
                if (parameterValue != null && !parameterValue.trim().isEmpty()) {
                    Object convertedValue = castParameterValue(parameterValue, fieldType);
                    invokeSetter(instance, clazz, fieldName, fieldType, convertedValue);
                }
            } else {
                // Attribut complexe (ex: Departement)
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

    /**
     * Méthode récursive pour traiter les objets imbriqués via un préfixe (ex: "departement.").
     */
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

    /**
     * Invoque le setter d'un champ ou y accède directement si le setter n'est pas présent.
     */
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
                // Ignore l'erreur si le champ est inaccessible
            }
        }
    }

    public static Object[] resolveArguments(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
            
        // Vérifier si la requête est multipart/form-data
        boolean isMultipart = request.getContentType() != null && 
                            request.getContentType().toLowerCase().startsWith("multipart/form-data");
            
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
        
            // 1. Injection de la Custom Session
            if (paramType == Session.class) {
                args[i] = new Session(request.getSession());
            } 
            // 2. Injection de la HttpSession standard
            else if (paramType == jakarta.servlet.http.HttpSession.class) {
                args[i] = request.getSession();
            }
            // 3. Injection du ServletContext (Évite l'erreur d'hydratation)
            else if (paramType == ServletContext.class) {
                args[i] = request.getServletContext();
            }
            // 4. Injection de la requête HTTP brute
            else if (paramType == HttpServletRequest.class) {
                args[i] = request;
            }
            // 5. Traitement des fichiers binaires FileUpload
            else if (paramType == FileUpload.class) {
                if (isMultipart) {
                    Part part = request.getPart(param.getName());
                    args[i] = processFileUpload(part);
                }
            } 
            // 6. Types simples / primitifs (String, int, Date...)
            else if (isBasicType(paramType)) {
                String paramName = param.getName();
                String paramValue = request.getParameter(paramName);
                args[i] = castParameterValue(paramValue, paramType);
            } 
            // 7. Objet métier / DTO complexe
            else {
                args[i] = hydrateObject(paramType, request);
            }
        }
    
        return args;
    }

    /**
     * Conversion d'une valeur String vers le type Java cible.
     */
    public static Object castParameterValue(String value, Class<?> type) {
        boolean vide = (value == null || (value.trim().isEmpty() && type != String.class));
        if (vide) {
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0.0;
            if (type == float.class) return 0f;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            return null;
        }

        if (type == String.class) return value;

        String v = value.trim();
        if (type == int.class || type == Integer.class) return Integer.parseInt(v);
        if (type == long.class || type == Long.class) return Long.parseLong(v);
        if (type == short.class || type == Short.class) return Short.parseShort(v);
        if (type == byte.class || type == Byte.class) return Byte.parseByte(v);
        if (type == double.class || type == Double.class) return Double.parseDouble(v);
        if (type == float.class || type == Float.class) return Float.parseFloat(v);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(v);
        if (type == char.class || type == Character.class) return v.charAt(0);
        if (type == java.sql.Date.class) return java.sql.Date.valueOf(v);
        if (type == java.util.Date.class) return new java.util.Date(java.sql.Date.valueOf(v).getTime());

        return value;
    }

    /**
     * Extrait un Part Servlet et le transforme en objet FileUpload du Framework
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
     * Charge une propriété depuis app.properties
     */
    public static String getProperty(String key) {
        try (InputStream input = Utilitaires.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (input == null) {
                return null;
            }
            java.util.Properties prop = new java.util.Properties();
            prop.load(input);
            return prop.getProperty(key);
        } catch (Exception e) {
            return null;
        }
    }
}