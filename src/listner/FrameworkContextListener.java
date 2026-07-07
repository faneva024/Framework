package listener;

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import annotations.Controllers;
import annotations.UrlMapping;
import exceptions.DuplicateRouteException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import models.Mapping;
import models.UrlMethode;
import utilitaires.Utilitaires;

@WebListener
public class FrameworkContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        
        // On récupère le nom du package depuis les paramètres globaux (web.xml ou annotation contextParam)
        String controllerPackage = context.getInitParameter("Controllers");
        
        if (controllerPackage == null || controllerPackage.isEmpty()) {
            context.log("Framework Error: Le paramètre d'initialisation global 'Controllers' est manquant.");
            throw new RuntimeException("Paramètre d'initialisation global 'Controllers' manquant.");
        }

        try {
            Map<UrlMethode, Mapping> urlMappings = new HashMap<>();
            
            // Récupération des classes annotées avec @Controllers
            List<String> listeController = Utilitaires.getClassByPackageAndAnnotation(
                    Controllers.class,
                    controllerPackage,
                    ElementType.TYPE);

            if (listeController != null) {
                for (String nomControleur : listeController) {
                    Class<?> classeControleur = Class.forName(controllerPackage + "." + nomControleur);
                    List<Method> methodes = Utilitaires.getMethodesAnnotees(classeControleur, UrlMapping.class);
                    
                    for (Method methode : methodes) {
                        UrlMapping annotation = methode.getAnnotation(UrlMapping.class);
                        String url = annotation.value();
                        String httpMethod = annotation.METHOD().toUpperCase(); // Sécurité : forcer en MAJUSCULE

                        UrlMethode key = new UrlMethode(url, httpMethod);
                        Mapping existingMapping = urlMappings.get(key);
                        
                        if (existingMapping != null) {
                            throw new DuplicateRouteException(url, httpMethod, existingMapping,
                                    new Mapping(nomControleur, methode.getName()));
                        }

                        urlMappings.put(key, new Mapping(nomControleur, methode.getName()));
                    }
                }
            }

            // Stockage de la table de routage globale dans le ServletContext
            context.setAttribute("routes", urlMappings);
            context.log("Framework initialized successfully with " + urlMappings.size() + " routes.");

        } catch (DuplicateRouteException e) {
            context.log("Framework Initialization Failed: Route dupliquée détectée -> " + e.getMessage());
            throw new RuntimeException("Erreur de mapping au démarrage : " + e.getMessage(), e);
        } catch (Exception e) {
            context.log("Framework Initialization Failed: " + e.getMessage());
            throw new RuntimeException("Erreur lors du chargement des contrôleurs : " + e.getMessage(), e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nettoyage si nécessaire
        sce.getServletContext().removeAttribute("routes");
    }
}