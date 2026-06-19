#!/bin/bash

# 1. Définition des variables
JAR_NAME="framework.jar"
SRC_DIR="src"
BIN_DIR="bin"

#  Vérifiez bien que ce JAR correspond à la bonne version (javax vs jakarta)
# Pour Jakarta EE 9+, préférez un nom explicite comme "lib/jakarta.servlet-api-6.0.0.jar"
JAKARTA_API_JAR="lib/servlet-api.jar"

echo " Nettoyage des anciens dossiers..."
rm -rf "$BIN_DIR"
rm -f "$JAR_NAME"
mkdir "$BIN_DIR"

echo " Compilation des fichiers Java..."
# Utilisation de find pour compiler tous les fichiers .java, même dans les sous-dossiers
find "$SRC_DIR" -name "*.java" > sources.txt
javac -cp "$JAKARTA_API_JAR" -d "$BIN_DIR" @sources.txt
RM_STATUS=$?
rm -f sources.txt

if [ $RM_STATUS -eq 0 ]; then
    echo " Compilation réussie. Création du fichier JAR..."
    
    # On se déplace dans le dossier bin
    cd "$BIN_DIR" || exit
    
    #  CORRECTION ICI : On utilise "." pour inclure TOUS les dossiers (packages) 
    # et non pas juste "*.class" à la racine.
    jar -cvf "../$JAR_NAME" .
    cd ..
    
    echo " Terminé ! Votre fichier '$JAR_NAME' est prêt."
    echo " Vous pouvez maintenant le copier dans le dossier WEB-INF/lib de votre autre projet."
else
    echo " Échec de la compilation. Vérifiez vos imports et le chemin de votre API Servlet."
    exit 1
fi