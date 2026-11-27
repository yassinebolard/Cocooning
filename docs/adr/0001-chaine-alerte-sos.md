# ADR 0001 : chaîne d'alerte S.O.S sur le téléphone récepteur

- Statut : **acceptée** par la MOA (Yassine) le 27/11/2026 : option A « en bonus », repli sur B
- Date : 27/11/2026
- Exigences : EF-SOS-03 à 08, EF-NTF-05 et 07, ENF-EVO-03 ; risques R1, R2, R7, R12
- Preuves : `docs/prototype-sos.md`

## Contexte

Le S.O.S doit réveiller le téléphone du partenaire et sonner fort, écran verrouillé, en
silencieux ou en Ne pas déranger, malgré l'optimisation batterie de One UI et de ColorOS, et
même sans services Google si un membre passe sous /e/OS. Depuis Android 15, une appli ne peut
plus changer d'elle-même l'état global du Ne pas déranger.

## Décision proposée

1. **Réveil par deux voies en parallèle**, derrière une même interface : FCM de données en
   priorité haute quand les services Google sont là, et UnifiedPush (ntfy auto hébergé sur le
   Proxmox) sinon. Le récepteur déduplique par identifiant d'alerte. Une connexion ntfy permanente
   tenue par un service au premier plan reste le dernier recours, activable dans les réglages.
2. **Sonnerie par un service au premier plan** qui joue l'alarme avec `MediaPlayer` sur
   `USAGE_ALARM`, en boucle, et non par le son du canal. Le canal de notification ne sert qu'au
   plein écran ; un second canal, sonore sur le flux alarme, sert de secours si le service ne
   peut pas démarrer.
3. **Ordre des gestes à la réception** : noter l'état d'origine (volume alarme, mode sonnerie,
   Ne pas déranger), repasser en mode sonnerie si l'accès Ne pas déranger est accordé, monter le
   volume alarme au maximum, jouer, vibrer, afficher le plein écran ; tout rétablir à l'arrêt,
   et au prochain démarrage de l'appli après un plantage.
4. **Assistant S.O.S obligatoire** à l'installation : notifications, plein écran, accès Ne pas
   déranger puis recréation automatique du canal S.O.S, exemption batterie, réglages constructeur
   guidés, puis un essai TEST réel de bout en bout. Contrôle quotidien des réglages lisibles et
   bandeau rouge si l'un manque.
5. **Pas de dépendance aux services Google pour la position** : `LocationManager` (fournisseur
   `fused` d'Android 12+, sinon GPS), compatible /e/OS ; carte osmdroid côté récepteur.

## Ne pas déranger : option A « en bonus », repli sur B

Avec l'accès Ne pas déranger, `setRingerMode(NORMAL)` fait sortir du Ne pas déranger sur Android
d'origine (émulateur Android 17, silence total compris), mais pas sur One UI 9 (S25+, mode
prioritaire : le Ne pas déranger reste actif ; silence total encore à essayer).

**Décision de la MOA (27/11/2026)** : A en bonus. Le même code tourne partout ; quand le
téléphone accepte, l'alerte traverse même le silence total, et quand il refuse, on retombe sur B
sans rien casser. Le levier n'est jamais une raison de sauter les exceptions Alarmes et
Cocooning dans le Ne pas déranger : l'assistant continue de les exiger.

Rappel des deux options étudiées :

- **A, garder ce levier** (recommandé si One UI se comporte pareil) : le S.O.S traverse même le
  silence total, à condition que l'utilisateur ait donné l'accès Ne pas déranger. L'écran
  d'alerte dit clairement que le Ne pas déranger a été levé, et l'appli le rétablit au mieux à
  l'arrêt (rétablissement imparfait mesuré : silence total revient en « prioritaire »).
- **B, s'en interdire** : ne jamais toucher au mode sonnerie quand un Ne pas déranger est actif,
  et accepter qu'en silence total l'alerte ne sonne pas (limite déjà annoncée en 6.5).

## Conséquences

- Le cahier, section 6.2, est à corriger sur deux points : l'hypothèse sur `setBypassDnd` est
  confirmée (il faut recréer le canal), et « Ce qui ne marche plus » est trop pessimiste sur
  Android d'origine, mais exact sur One UI.
- La voie FCM impose un projet Firebase et une clé de compte de service sur le serveur, jamais
  dans le dépôt (ENF-SEC-11).
- Les essais hebdomadaires en mode TEST restent la seule preuve pour les réglages que l'appli ne
  peut pas lire.
