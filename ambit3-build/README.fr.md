# Ambit3 1

Version d’essai Android pour la Suunto Ambit3 Sport, basée sur [Sommet v0.2.59](https://github.com/skinnie/sommet/tree/v0.2.59), sous GPL-3.0.

- Les pas sont lus dans l’historique de la montre. L’accueil affiche le compteur du jour avec une action d’actualisation. Le menu Historique mène aux données de santé de Sommet.
- La synchronisation Bluetooth tente la lecture des pas avant celle des séances. Les messages de connexion et de transfert s’affichent dans l’application.
- Les préréglages Course GPS, Marche GPS, Rando GPS et Velo GPS utilisent les types de sport de l’Ambit3 Sport. Dans Modes sportifs, créer un mode, choisir un préréglage puis confirmer l’envoi. La zone des modes est sauvegardée par Sommet avant écriture et relue après.
- Le transfert des appels et SMS vers la montre reste à implémenter. Les identifiants d’application Strava ne sont pas inclus dans cette version.
- L’identifiant Android indépendant permet d’installer Ambit3 à côté de Sommet. Fermer les autres applications qui communiquent avec la montre pendant les essais. Les deux applications ont leurs propres données.

## Reconstruction de l’APK fourni

1. Récupérer [l’APK Sommet v0.2.59](https://github.com/skinnie/sommet/releases/download/v0.2.59/Sommet_v0.2.59.apk). Le fichier `base.sha256` identifie le binaire utilisé.
2. Avec Node 22 ou ultérieur, exécuter `npm ci` dans le dossier `android`.
3. Dans ce dossier, exécuter `NODE_ENV=production npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output /tmp/ambit3.js`.
4. Compiler le fichier avec `node_modules/hermes-compiler/hermesc/linux64-bin/hermesc -O -emit-binary -out /tmp/ambit3.bundle /tmp/ambit3.js`.
5. Depuis la racine, exécuter `python3 ambit3-build/repackage.py APK_ORIGINAL /tmp/ambit3.bundle APK_NON_SIGNE`.
6. Aligner avec `zipalign -P 16 -f 4 APK_NON_SIGNE APK_ALIGNE`, puis signer l’APK aligné avec `apksigner` et votre clé Android.

Cette méthode réutilise les binaires natifs ARM64 de la publication Sommet. Le paquet source inclut les modifications et le code de Sommet. La clé privée de signature reste séparée des sources.

## Mise à jour Ambit3.2 — 7 novembre 2026

- Accueil dédié à l’Ambit3 : Aujourd’hui, Historique, Sports, Ma montre.
- Pas issus des lectures réelles, graphique hebdomadaire ; « Non lus » distinct de zéro.
- Dictionnaire français activé. Certains écrans avancés conservent des libellés anglais.
- Modes de sport : accès à l’éditeur existant et ses contrôles de sauvegarde/relecture.
- Service Android `connectedDevice` avec notification permanente et arrêt explicite.
  Choix 15/30/60 minutes dans Ma montre, après connexion Bluetooth.
- Le service emploie AlarmManager (alarmes inexactes autorisées au repos), un verrou CPU
  limité à deux minutes lors d’un cycle, et un événement natif vers le contexte React
  déjà vivant. Les cycles ne sont pas des rendez-vous exacts. La montre doit accepter
  une connexion. Aucune recherche Bluetooth périodique générale : la reconnexion
  passive est réarmée pour la montre déjà appairée.
- Les transferts occupés sont ignorés ; un transfert récent (moins de cinq minutes)
  n’est pas relancé. L’arrêt du cycle n’interrompt pas une opération déjà en cours.
- Pas de redémarrage au démarrage du téléphone ni après arrêt forcé du processus.
  Réactiver dans Ma montre après relance. Android/le fabricant peut arrêter le service.
- Notification de service silencieuse. Aucun transfert des notifications du téléphone
  vers la montre et aucun bip personnalisé sur la montre dans cette version.

### Construction de l’extension native

Cette livraison compile le Java de l’extension et le bundle Hermes, puis réutilise
les DEX et bibliothèques ARM64 de Sommet 0.2.59 sans les modifier. Ce n’est pas une
recompilation Gradle complète de Sommet. Le manifeste est recompilé avec apktool
2.11.1 ; ses références de ressources existantes sont comparées à celles de la base.

1. Décoder l’APK officiel : `java -jar apktool.jar d -s original.apk -o decoded`.
2. Exécuter `python3 ambit3-build/prepare-background-manifest.py decoded` une seule fois.
3. Recompiler : `java -jar apktool.jar b decoded -o manifest-build.apk`.
4. Compiler `ambit3-build/native/com/ambitsportlocal/background/*.java` avec javac,
   `-source 8 -target 8 -classpath android-36/android.jar -d addon-classes`.
5. Transformer les classes en DEX avec Android build-tools 36.0.0 `d8`, `--min-api 28`.
6. Recréer le bundle Metro/Hermes comme décrit plus haut.
7. Exécuter repackage.py avec les trois arguments positionnels existants, puis
   `--manifest-apk manifest-build.apk --addon-dex addon-dex/classes.dex`.
8. zipalign `-P 16`, puis signature avec la même clé privée que la version précédente.

L’activité de contrôle est accessible par Intent depuis React Native, exportée
uniquement avec une permission de niveau signature propre à l’application. Le
service est non exporté. L’accès au contexte React repose sur les méthodes publiques
`getReactHost`, `getCurrentReactContext`, `emitDeviceEvent` de la base non obfusquée.
Ces noms devront être revérifiés si la base native change.

### Vérifications réalisées

58 suites Jest réussies, 351 tests réussis, 1 ignoré. Tests d’interface : données
absentes, blocage des transferts hors connexion/en cours, accès aux modes, arrêt
accessible du service. Compilation Java et Hermes réussie. Vérifications APK :
signature v3, alignement 16K, archive ZIP, DEX de base et bibliothèques ARM64
identiques à l’original, DEX additionnel, identité/package/version, permissions.
Le contrôle TypeScript global conserve des erreurs antérieures hors des fichiers
modifiés ; il n’est pas entièrement vert. Aucun test réel sur Android ou sur la
montre n’a été possible. Le service en arrière-plan doit être considéré expérimental.

### Documentation consultée

- https://github.com/skinnie/sommet (base v0.2.59, licence GPL-3.0)
- https://developer.android.com/develop/connectivity/bluetooth/ble/background
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://www.suunto.com/Support/Product-support/suunto_ambit3_sport/suunto_ambit3_sport/features/notifications/
- https://www.suunto.com/fr-fr/Assistance/faq-articles/suunto-app/comment-gerer-les-notifications-avec-lappli-suunto-android/

La notice Suunto confirme le bip standard lors d’une notification reçue. Elle ne
constitue pas une documentation de commande Bluetooth ni la preuve qu’un son
personnalisé peut être chargé. Aucun mécanisme validé pour cela n’a été identifié.

## Variante Ambit3 ANCS de test

Identité Android `com.ambitsporttrial`, signature de développement, ARM64.
Elle s’installe à côté d’Ambit3-2. Le module BLE, son service Android d’accès
aux notifications, le codec ANCS et les classes de localisation/synchronisation
sont compilés depuis les sources. Les bibliothèques JNI et les autres classes
proviennent de la base Sommet 0.2.59 contrôlée par SHA-256.

Compiler les fichiers Kotlin AmbitBleModule.kt et AmbitNotificationListener.kt
avec Kotlin 2.2, android.jar API 36, React Android 0.84.1 et androidx.core 1.16.0.
Compiler AncsFrames.java et les classes background Java. Transformer les
classes générées en DEX avec d8 min-api 28. Exécuter StripBle.java avec
apktool 2.11.1 sur l’APK original pour obtenir les DEX de remplacement.
Recompiler le manifeste avec le paquet com.ambitsporttrial, le service
AmbitNotificationListener et la visibilité des activités de lancement.
L’action de contrôle de synchro doit être com.ambitsporttrial.SYNC_CONTROL.
Puis utiliser repackage.py avec --package-name com.ambitsporttrial,
--replacement-dex-dir et --addon-dex, aligner et signer.

Le relais ANCS nécessite l’accès spécial aux notifications Android, le choix
des applications dans Ma montre et un abonnement Bluetooth de la montre.
Essai matériel requis. Le service de notifications n’envoie rien tant qu’une
application n’a pas été sélectionnée. Le transfert des appels et messages
dépend des données que la version Android communique au service.
