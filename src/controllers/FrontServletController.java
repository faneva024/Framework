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
import jakarta.servlet.http.HttpSession;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import models.Mapping;
import models.ModelView;
import models.UrlMethode;
import utilitaires.Utilitaires;

import com.google.gson.Gson;
import annotations.ApiRest;
import annotations.Auth;

@MultipartConfig

public class FrontServletController extends HttpServlet {

    @Override
    public void init() throws ServletException {
        super.init();
        // 1. Récupération de la clé utilisateur en session
        String sessionKey = getServletContext().getInitParameter("sessionName");
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            throw new ServletException("Paramètre global 'sessionName' manquant dans le web.xml");
        }
        getServletContext().setAttribute("sessionKey", sessionKey);

        // 2. Récupération de la clé du rôle en session (Sprint 10)
        String sessionRoleKey = getServletContext().getInitParameter("sessionRoleName");
        if (sessionRoleKey == null || sessionRoleKey.trim().isEmpty()) {
            throw new ServletException("Paramètre global 'sessionRoleName' manquant dans le web.xml");
        }
        getServletContext().setAttribute("sessionRoleKey", sessionRoleKey);
    }

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

            // -----------------------------------------------------------------
            // 4-VÉRIFICATION D'AUTHENTIFICATION ET DES RÔLES (@Auth) - SPRINT 10
            // -----------------------------------------------------------------
            Auth authAnnotation = method.getAnnotation(Auth.class);
            if (authAnnotation == null) {
                authAnnotation = controllerClass.getAnnotation(Auth.class);
            }

            if (authAnnotation != null || controllerClass.isAnnotationPresent(Auth.class)) {
                HttpSession httpSession = req.getSession(false);
                
                String sessionKey = (String) getServletContext().getAttribute("sessionKey");
                String sessionRoleKey = (String) getServletContext().getAttribute("sessionRoleKey");

                Object user = (httpSession != null) ? httpSession.getAttribute(sessionKey) : null;

                // 1. Contrôle d'authentification : l'utilisateur doit être connecté
                if (user == null) {
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Accès refusé : Vous devez être connecté pour accéder à cette ressource.");
                    return;
                }

                // 2. Contrôle d'autorisation par rôle (si spécifié dans @Auth)
                if (authAnnotation != null && authAnnotation.role().length > 0) {
                    String[] allowedRoles = authAnnotation.role();
                    Object userRoleObj = (httpSession != null) ? httpSession.getAttribute(sessionRoleKey) : null;
                    String userRole = (userRoleObj != null) ? userRoleObj.toString() : "";

                    boolean hasRole = false;
                    for (String role : allowedRoles) {
                        if (role.equalsIgnoreCase(userRole)) {
                            hasRole = true;
                            break;
                        }
                    }

                    if (!hasRole) {
                        res.sendError(HttpServletResponse.SC_FORBIDDEN, "Accès interdit : Votre rôle (" + userRole + ") ne vous autorise pas à exécuter cette action.");
                        return;
                    }
                }
            }

            // Résolution des arguments (Data Binding)
            Object[] args = Utilitaires.resolveArguments(method, req);

            // Invocation de la méthode d'action
            Object retour = method.invoke(controller, args);

            // Traitement de la réponse
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