package controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
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

@MultipartConfig // OBLIGATOIRE : Permet à Tomcat d'intercepter les requêtes multipart/form-data
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

            WebApplicationContext springContext = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());

            String controllerPackage = getServletContext().getInitParameter("Controllers");
            Class<?> controllerClass = Class.forName(controllerPackage + "." + mapping.getNomClasse());
            Object controller = springContext.getBean(controllerClass);
            
            Method method = mapping.getMethode();

            // Résolution des arguments (Supporte Types Simples, Objets et FileUpload)
            Object[] args = Utilitaires.resolveArguments(method, req);

            Object retour = method.invoke(controller, args);

            // Traitement @ApiRest
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
                // Comportement classique
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