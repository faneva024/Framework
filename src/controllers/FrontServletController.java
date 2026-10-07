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

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import models.Mapping;
import models.ModelView;
import models.UrlMethode;
import utilitaires.Utilitaires;

import com.google.gson.Gson;
import annotations.ApiRest;

public class FrontServletController extends HttpServlet {


    @SuppressWarnings("unchecked")
    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        
        Map<UrlMethode, Mapping> urlMappings = (Map<UrlMethode, Mapping>) getServletContext().getAttribute("routes");

        if (urlMappings == null) {
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur Interne: La table de routage n'a pas été initialisée.");
            return;
        }

        String methode = req.getMethod().toUpperCase();
        String url = req.getRequestURI();
        String contexte = req.getContextPath();
        String urlRecherchee = url.substring(contexte.length());

        Mapping mapping = urlMappings.get(new UrlMethode(urlRecherchee, methode));

        if (mapping == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "URL indéfinie ou méthode HTTP non supportée : " + urlRecherchee + " [" + methode + "]");
            return;
        }

        try {
            res.setContentType("text/html;charset=UTF-8");

            // 1. Récupération du conteneur Spring
            WebApplicationContext springContext = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());

            // 2. Instanciation dynamique du contrôleur via Spring
            String controllerPackage = getServletContext().getInitParameter("Controllers");
            Class<?> controllerClass = Class.forName(controllerPackage + "." + mapping.getNomClasse());
            Object controller = springContext.getBean(controllerClass);
            
            // 3.  Recuperation de la Method
            Method method = mapping.getMethode();

            // 4. Délégation totale du Binding (extraction des arguments) à Utilitaires
            Object[] args = Utilitaires.resolveArguments(method, req);

            // 5. Invocation de la méthode
            Object retour = method.invoke(controller, args);

            // -----------------------------------------------------------
            // Traitement de l'annotation @ApiRest
            // -----------------------------------------------------------
            if (method.isAnnotationPresent(ApiRest.class)) {
                res.setContentType("application/json;charset=UTF-8");
                PrintWriter out = res.getWriter();

                if (retour instanceof String) {
                    out.print((String) retour);
                } else if (retour != null) {
                    Gson gson = new Gson();
                    out.print(gson.toJson(retour));
                }
            } else {
                // ----------------------------------------------------------
                // ModelView ou HTML brut
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
            throw new ServletException("Erreur lors de l'exécution de l'action : " + mapping.getNomClasse() + "." + mapping.getMethode().getName(), e);
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