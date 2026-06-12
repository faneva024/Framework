package Controllers;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/*")
public class FrontControllerServlet extends HttpServlet {

    // Méthode centrale pour regrouper la logique
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Récupération de l'URL et de l'URI
        String url = request.getRequestURL().toString();
        String uri = request.getRequestURI();

        // Affichage dans la console du serveur
        System.out.println("URL appelée : " + url);
        System.out.println("URI appelée : " + uri);
        
        // Optionnel : Affichage direct sur la page web du client
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().println("<h1>Front Controller</h1>");
        response.getWriter().println("<p>Vous avez demandé l'URL : " + url + "</p>");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // On redirige vers notre méthode centrale
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // On fait de même pour les requêtes POST
        processRequest(request, response);
    }
}
