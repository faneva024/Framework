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
            PrintWriter out = res.getWriter();

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

            // GESTION DU RETOUR (ModelView ou String classique)
            if (retour instanceof ModelView) {
                ModelView mv = (ModelView) retour;
                
                // Récupération des paramètres de préfixe et de suffixe (termes Spring)
                String prefix = getServletContext().getInitParameter("prefix");
                String suffix = getServletContext().getInitParameter("suffix");
                
                // Sécurité au cas où les paramètres ne seraient pas définis dans web.xml
                if (prefix == null) prefix = "";
                if (suffix == null) suffix = "";
                
                // Injection des données du ModelView dans la requête HTTP
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
                
                // Construction du chemin final et transfert (Forward)
                String viewPath = prefix + mv.getUrl() + suffix;
                RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
                dispatcher.forward(req, res);
                
            } else if (retour != null) {
                // Rendu du résultat classique (String ou autre)
                out.println(retour.toString());
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