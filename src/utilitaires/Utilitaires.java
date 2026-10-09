package utilitaires;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;

import jakarta.servlet.http.HttpServletRequest;

public class Utilitaires {

    public static List<String> getClassByPackageAndAnnotation(Class<? extends Annotation> annotation,
            String packageName, ElementType type)
            throws Exception {
        try {
            if (typeElementValide(annotation, type)) {
                List<String> listeClass = new ArrayList<>();
                List<Class<?>> classInPackage = getClassByPackage(packageName);
                List<Class<?>> classByAnnotation = filterByAnnotation(classInPackage, annotation);

                for (Class<?> class1 : classByAnnotation) {
                    listeClass.add(class1.getSimpleName());
                }

                return listeClass;
            }
        } catch (Exception e) {
            throw e;
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

    /**
     * NOUVEAUTÉ SPRINT 7-bis : Vérifie si la classe est un type de base/primitif
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
     * NOUVEAUTÉ SPRINT 7-bis : Instancie et remplit un objet (JavaBean) à partir de la requête HTTP
     */
    public static Object hydrateObject(Class<?> clazz, HttpServletRequest request) throws Exception {
        // 1. Instanciation de l'objet via son constructeur sans argument
        Object instance = clazz.getDeclaredConstructor().newInstance();

        // 2. Parours de tous les champs de la classe (et superclasses si besoin)
        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            String parameterValue = request.getParameter(fieldName);

            // Si le paramètre existe dans la requête HTTP
            if (parameterValue != null && !parameterValue.trim().isEmpty()) {
                // Conversion de la valeur vers le type de l'attribut
                Object convertedValue = castParameterValue(parameterValue, field.getType());

                // Construction du nom du setter (ex: "nom" -> "setNom")
                String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

                try {
                    // Tentative d'appel du setter public
                    Method setter = clazz.getMethod(setterName, field.getType());
                    setter.invoke(instance, convertedValue);
                } catch (NoSuchMethodException e) {
                    // Si pas de setter public, écriture directe dans le champ privé
                    field.setAccessible(true);
                    field.set(instance, convertedValue);
                }
            }
        }

        return instance;
    }

    /**
     * Résolution des arguments de la méthode (Supporte Types Simples ET Objets)
     */
    public static Object[] resolveArguments(Method method, HttpServletRequest request) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            if (isBasicType(paramType)) {
                // Cas 1 : Type simple (String, int, Date...)
                String paramName = param.getName();
                String paramValue = request.getParameter(paramName);
                args[i] = castParameterValue(paramValue, paramType);
            } else {
                // Cas 2 : Type complexe (Objet/DTO) - Hydratation automatique
                args[i] = hydrateObject(paramType, request);
            }
        }

        return args;
    }

    /**
     * Conversion d'une valeur String vers le type Java cible
     */
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