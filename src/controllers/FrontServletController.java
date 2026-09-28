package controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Importations Spring nécessaires pour récupérer le conteneur IoC
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import models.Mapping;
import models.ModelView;
import models.UrlMethode;

import com.google.gson.Gson;
import annotations.ApiRest;

public class FrontServletController extends HttpServlet {

    @SuppressWarnings("unchecked")
    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        
        // Récupération de la table de routage générée par le Listener
        Map<UrlMethode, Mapping> urlMappings = (Map<UrlMethode, Mapping>) getServletContext().getAttribute("routes");

        if (urlMappings == null) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Erreur Interne: La table de routage n'a pas été initialisée.");
            return;
        }

        String methode = req.getMethod().toUpperCase();
        String url = req.getRequestURI();
        String contexte = req.getContextPath();
        String urlRecherchee = url.substring(contexte.length());

        Mapping mapping = urlMappings.get(new UrlMethode(urlRecherchee, methode));

        if (mapping == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().println("<h3>URL indéfinie ou méthode HTTP non supportée : " + urlRecherchee + " [" + methode + "]</h3>");
            return;
        }

        try {
            res.setContentType("text/html;charset=UTF-8");
            // PrintWriter out = res.getWriter();

            // 1. Récupération du conteneur Spring (ApplicationContext) associé au ServletContext
            WebApplicationContext springContext = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());

            // 2. Récupération du nom du package depuis le ServletContext
            String controllerPackage = getServletContext().getInitParameter("Controllers");
            
            // 3. Récupération de la classe du contrôleur par son nom complet
            Class<?> controllerClass = Class.forName(controllerPackage + "." + mapping.getNomClasse());
            
            // 4. RÉSOLUTION DE LA CORRECTION : 
            // On demande l'instance (le Bean) directement à Spring au lieu d'utiliser "newInstance()".
            // De cette manière, l'instance récupérée possède toutes ses dépendances (@Autowired, @Service...) fonctionnelles.
            Object controller = springContext.getBean(controllerClass);
            
            // 5. Récupération et invocation dynamique de la méthode cible
            Method method = controllerClass.getDeclaredMethod(mapping.getNomMethode());
            Object retour = method.invoke(controller);

            // -----------------------------------------------------------
            // Traitement de l'annotation @ApiRest
            // -----------------------------------------------------------
            if (method.isAnnotationPresent(ApiRest.class)) {
                res.setContentType("application/json;charset=UTF-8");
                PrintWriter out = res.getWriter();

                if (retour instanceof String) {
                    // Si la méthode renvoie déjà une chaîne de caractères
                    out.print((String) retour);
                } else if (retour != null) {
                    // Pour tout autre objet (List, Map, Objet métier...), conversion automatique en JSON
                    Gson gson = new Gson();
                    out.print(gson.toJson(retour));
                }
            } else {
                // ----------------------------------------------------------
                // COMPORTEMENT CLASSIQUE (ModelView ou HTML brut)
                // -----------------------------------------------------------
                if (retour instanceof ModelView) {
                    ModelView mv = (ModelView) retour;
                    String prefix = getServletContext().getInitParameter("prefix");
                    String suffix = getServletContext().getInitParameter("suffix");
                    
                    if (prefix == null) prefix = "";
                    if (suffix == null) suffix = "";
                    
                    for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                        req.setAttribute(entry.getKey(), entry.getValue());
                    }
                    
                    String viewPath = prefix + mv.getUrl() + suffix;
                    RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
                    dispatcher.forward(req, res);
                    
                } else if (retour != null) {
                    res.setContentType("text/html;charset=UTF-8");
                    res.getWriter().println(retour.toString());
                }
            }

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'exécution de l'action : " + mapping.getNomClasse() + "." + mapping.getNomMethode(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }

    protected void envoyer(HttpServletRequest req, HttpServletResponse res, String path)
            throws ServletException, IOException {
        RequestDispatcher requestDispatcher = req.getRequestDispatcher(path);
        requestDispatcher.forward(req, res);
    }
}