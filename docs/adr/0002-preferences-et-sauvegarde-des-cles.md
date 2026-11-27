# ADR 0002 : préférences chiffrées et sauvegarde des clés pour le changement de téléphone

- Statut : **acceptée** par la MOA (Yassine) le 27/11/2026 : phrase de récupération choisie par l'utilisateur
- Date : 27/11/2026
- Exigences : ENF-SEC-02, ENF-SEC-04, EF-CPT-08, EF-CPT-09
- Code : module `:core:securite` (`PreferencesChiffrees`, paquet `sauvegarde`)

## Contexte

Les bases Room sont chiffrées par SQLCipher ; leur phrase de passe est enveloppée par une clé de
l'Android Keystore qui ne quitte jamais le téléphone, et tout est exclu des sauvegardes Android.
Il restait deux trous : les préférences (seconde moitié de ENF-SEC-04) et le changement de
téléphone, puisqu'une clé qui ne sort pas du téléphone disparaît avec lui (EF-CPT-08). Le serveur
Cocooning prévu par EF-CPT-08 n'existe pas encore.

## Décision

### Préférences chiffrées

- DataStore Preferences (1.3.0) dont le sérialiseur est enveloppé : le fichier est un bloc
  AES-256-GCM, clé du Keystore sous l'alias `cocooning_preferences` (distinct des bases), avec le
  nom du magasin authentifié (un fichier copié d'un magasin à l'autre est refusé).
- Fichier dans `no_backup/preferences/`, jamais sauvegardé par Android, comme les phrases de
  passe des bases. Fichier illisible (clé perdue) : préférences remises à zéro, l'appli reste
  utilisable.
- `EncryptedSharedPreferences` (security-crypto) écarté : déprécié par Google.
- Migration : les SharedPreferences en clair nommées à l'ouverture sont recopiées puis supprimées
  par `SharedPreferencesMigration`. À ce jour l'appli n'en a aucune ; `osmdroid.xml` reste en
  clair car la bibliothèque de carte le lit elle-même et il ne contient que des réglages techniques.

### Sauvegarde des clés

- Un fichier unique contient chaque base (copie encore chiffrée par SQLCipher, faite par
  `sqlcipher_export` sans fermer Room), sa phrase de passe et les préférences ; le tout chiffré
  en AES-256-GCM par une clé dérivée de la **phrase de récupération choisie par l'utilisateur**.
- Dérivation Argon2id (Bouncy Castle 1.87), 64 Mio, 3 passes, sel aléatoire de 16 octets ;
  environ 2,2 s sur le S25+ en version de débogage. Les paramètres sont écrits et authentifiés
  dans l'en-tête : on pourra les relever sans casser les anciens fichiers, et les baisser dans un
  fichier le rend illisible. Bornes à la lecture contre un fichier piégé.
- Phrase : 20 caractères au moins, estimation prudente de sa robustesse, refus en dessous de
  45 bits estimés, « solide » à partir de 60. Les majuscules, les espaces en trop et les
  variantes Unicode ne comptent pas, pour éviter un échec de restauration à cause d'un clavier.
- En attendant le serveur, l'utilisateur exporte le fichier et le range où il veut (sélecteur de
  fichiers d'Android) : aucun service tiers imposé. Le même fichier partira chaque jour vers le
  serveur quand il existera ; le serveur ne pourra pas le lire.
- Restauration : vérification de la phrase d'abord (rien n'est modifié), puis remise en place des
  bases connues de la version installée seulement, phrases de passe réenveloppées par le Keystore
  du nouveau téléphone, puis redémarrage de l'appli.

## Conséquences

- Une phrase choisie reste devinable si elle est banale ; le fichier peut être attaqué hors ligne
  sans limite d'essais. Le texte de l'écran de création doit le dire clairement.
- Phrase oubliée : la sauvegarde est perdue, personne ne peut la rouvrir. À dire aussi.
- Bouncy Castle ajoute du poids à l'APK tant que R8 n'est pas activé en version publiée.
- Écrans (module `:feature:sauvegarde`) :
  - Création de la phrase et export, depuis l'accueil : phrase saisie deux fois, masquée par
    défaut, clavier sans suggestions ; indicateur de robustesse en trois paliers doublé d'un texte ;
    encadré « Phrase oubliée, sauvegarde perdue » et case à cocher obligatoire ; fichier
    `cocooning-sauvegarde-AAAA-MM-JJ.cocooning` rangé par le sélecteur de fichiers d'Android. La
    phrase n'est jamais enregistrée et chaque export la redemande.
  - Restauration au premier lancement seulement : un écran d'accueil propose « Commencer » ou
    « Restaurer une sauvegarde ». Un téléphone qui a déjà une base ne le voit jamais (mise à jour
    sans effet sur les données). Fichier refusé tout de suite s'il n'est pas une sauvegarde ; phrase
    fausse signalée sur le champ, sans rien modifier ; puis redémarrage de l'appli.
  - Rappel d'export : carte sur l'accueil sans sauvegarde, puis 30 jours après la dernière ;
    « Plus tard » la cache 7 jours. Lien permanent « Sauvegarder mes données » en bas de l'accueil
    en attendant un écran de réglages.
- Restent : envoi quotidien au serveur ; transfert par QR code (EF-CPT-09) ; R8 en version publiée.
