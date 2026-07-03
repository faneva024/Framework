package controllers;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import annotations.Controllers;
import annotations.UrlMapping;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import exceptions.DuplicateRouteException;
import models.*;
import utilitaires.Utilitaires;

public class FrontServletController extends HttpServlet {
    protected List<String> listeController;
    protected Map<UrlMethode, Mapping> urlMappings;
    protected String startupError;

    @Override
    public void init() throws ServletException {
        startupError = null;
        try {
            String controllerPackage = this.getInitParameter("Controllers");
            if (controllerPackage == null || controllerPackage.isEmpty()) {
                startupError = "Parametre d'initialisation Controllers manquant.";
                return;
            }

            listeController = Utilitaires.getClassByPackageAndAnnotation(
                    Controllers.class,
                    controllerPackage,
                    ElementType.TYPE);

            urlMappings = new HashMap<>();
            registerMappings(controllerPackage);
        } catch (DuplicateRouteException e) {
            startupError = e.getMessage();
            log("Erreur de mapping: " + startupError, e);
        } catch (Exception e) {
            startupError = "Erreur lors de l'initialisation du FrontServletController : " + e.getMessage();
            log(startupError, e);
        }
    }

    private void registerMappings(String controllerPackage) throws Exception {
        for (String nomControleur : listeController) {
            Class<?> classeControleur = Class.forName(controllerPackage + "." + nomControleur);
            List<Method> methodes = Utilitaires.getMethodesAnnotees(classeControleur, UrlMapping.class);
            for (Method methode : methodes) {
                addRouteMapping(nomControleur, methode);
            }
        }
    }

    private void addRouteMapping(String nomControleur, Method methode) throws DuplicateRouteException {
        UrlMapping annotation = methode.getAnnotation(UrlMapping.class);
        String url = annotation.value();
        String httpMethod = annotation.METHOD();

        UrlMethode key = new UrlMethode(url, httpMethod);
        Mapping existingMapping = urlMappings.get(key);
        if (existingMapping != null) {
            throw new DuplicateRouteException(url, httpMethod, existingMapping,
                    new Mapping(nomControleur, methode.getName()));
        }

        urlMappings.put(key, new Mapping(nomControleur, methode.getName()));
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

    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        if (startupError != null) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/plain;charset=UTF-8");
            PrintWriter out = res.getWriter();
            out.println("Erreur de configuration du framework:");
            out.println(startupError);
            return;
        }

        PrintWriter out = res.getWriter();
        //  for (String controllerName : listeController) {
        //     // out.println(controllerName);
        // }

        String methode = req.getMethod();
        String url = req.getRequestURI();
        String contexte = req.getContextPath();
        String urlRecherchee = url.substring(contexte.length());

        Mapping mapping = urlMappings.get(new UrlMethode(urlRecherchee, methode));

        if (mapping == null) {
            out.println("url indefini : " + urlRecherchee);
        } else {

            res.setContentType("text/html;charset=UTF-8");

            out.println("URL : " + urlRecherchee);
            out.println("<br>");
            out.println("Controller : " + mapping.getNomClasse());
            out.println("<br>");
            out.println("Methode : " + mapping.getNomMethode());
            try {
                Class<?> controllerClass = Class.forName(getInitParameter("Controllers")+ "." + mapping.getNomClasse());

                Object controller = controllerClass.getDeclaredConstructor().newInstance();

                Method method = controllerClass.getDeclaredMethod(mapping.getNomMethode());

                Object retour = method.invoke(controller);

                out.println("<br><br>");
                out.println(retour);

            }
            catch (Exception e) {
                throw new ServletException(e);
            }
        
        }
    }
}