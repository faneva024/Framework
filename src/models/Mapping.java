package models;

import java.lang.reflect.Method;

public class Mapping {
    private String nomClasse;
    private Method methode; // Remplacement : String nomMethode -> Method methode

    public Mapping(String nomClasse, Method methode) {
        this.nomClasse = nomClasse;
        this.methode = methode;
    }

    public String getNomClasse() {
        return nomClasse;
    }

    public void setNomClasse(String nomClasse) {
        this.nomClasse = nomClasse;
    }

    public Method getMethode() {
        return methode;
    }

    public void setMethode(Method methode) {
        this.methode = methode;
    }
}