#!/bin/bash

# 1. Définition des variables
JAR_NAME="framework.jar"
SRC_DIR="src"
BIN_DIR="bin"

# 2. Nettoyage des anciens dossiers
echo "🧹 Nettoyage des anciennes compilations..."
rm -rf "$BIN_DIR"
rm -f "$JAR_NAME"
mkdir "$BIN_DIR"

echo "⚙️ Compilation des fichiers Java..."

# Utilisation de find pour lister tous les fichiers .java
find "$SRC_DIR" -name "*.java" > sources.txt

# CORRECTION DU CLASSPATH :
# On inclut TOUS les JARs du dossier "lib" (servlet-api.jar + les JARs de Spring)
# Sous Linux, l'utilisation de "lib/*" est la méthode officielle pour charger tout un dossier de JARs.
javac -cp "lib/*" -d "$BIN_DIR" @sources.txt

COMPILE_STATUS=$?
rm -f sources.txt

if [ $COMPILE_STATUS -eq 0 ]; then
    echo "✅ Compilation réussie. Création du fichier JAR..."
    
    # Déplacement dans le dossier bin pour empaqueter
    cd "$BIN_DIR" || exit
    
    # Création du JAR avec toutes les classes du framework
    jar -cvf "../$JAR_NAME" .
    cd ..
    
    echo "🎉 Le fichier '$JAR_NAME' est prêt et à jour !"
else
    echo "❌ Échec de la compilation. Vérifiez vos imports ou la présence des JARs Spring dans le dossier 'lib'."
    exit 1
fi