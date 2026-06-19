package Controllers;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.io.PrintWriter;
import java.util.List;

import annotations.Controllers;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import utilitaires.Utilitaires;

public class FrontServletController extends HttpServlet {
    protected List<String> listeController;

    @Override
    public void init() throws ServletException {
        try {
            if (!this.getInitParameter("Controllers").isEmpty()) { 
                String ControllerPackage = this.getInitParameter("Controllers");
                listeController = Utilitaires.getClassByPackageAndAnnotation(Controllers.class, ControllerPackage,
                        ElementType.TYPE);
            }

        } catch (Exception e) {
            throw new ServletException(e);
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

    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        PrintWriter out = res.getWriter();

        for (String controllerName : listeController) {
            out.println(controllerName);
        }
    }
}