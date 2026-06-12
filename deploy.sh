#!/bin/bash

echo "=== Nettoyage et compilation du Framework ==="
# On compile les classes avec Maven (gère le classpath)
mvn clean compile

echo "=== Création du JAR du Framework ==="
# On crée le dossier target s'il n'existe pas
mkdir -p target

# On utilise l'outil jar de Java pour empaqueter le dossier des classes compilées
jar -cf target/sprint0.jar -C target/classes .

echo "=== JAR créé avec succès dans target/ ==="