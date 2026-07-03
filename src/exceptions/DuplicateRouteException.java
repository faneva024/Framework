package exceptions;

import models.Mapping;

public class DuplicateRouteException extends Exception {
    public DuplicateRouteException(String url, String httpMethod, Mapping firstMapping, Mapping secondMapping) {
        super("Route dupliquee detectee pour URL='" + url + "' et la methode ='" + httpMethod + "' entre "
                + firstMapping.getNomClasse() + "." + firstMapping.getNomMethode() + " et "
                + secondMapping.getNomClasse() + "." + secondMapping.getNomMethode());
    }
}
