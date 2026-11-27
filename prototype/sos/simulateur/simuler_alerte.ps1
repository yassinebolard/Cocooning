# Simulateur minimal : publie une alerte S.O.S TEST sur le sujet ntfy du prototype,
# comme le ferait le futur serveur Cocooning. Aucun backend à installer.
#
# Exemple :
#   .\simuler_alerte.ps1 -Sujet cocooning-proto-xxxxxxxxxxxxxxxx
#   .\simuler_alerte.ps1 -Sujet cocooning-proto-xxxx -Lat 48.8566 -Lon 2.3522 -Precision 12
param(
    [Parameter(Mandatory = $true)][string]$Sujet,
    [string]$Serveur = "https://ntfy.sh",
    [string]$De = "simulateur PC",
    [double]$Lat = [double]::NaN,
    [double]$Lon = [double]::NaN,
    [int]$Precision = 15
)

$id = "sim-" + [guid]::NewGuid().ToString().Substring(0, 6)
$emis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$alerte = [ordered]@{ type = "sos"; id = $id; emis = $emis; de = $De; test = $true; batterie = 100 }
if (-not [double]::IsNaN($Lat)) { $alerte.lat = $Lat; $alerte.lon = $Lon; $alerte.precision = $Precision }

$corps = [System.Text.Encoding]::UTF8.GetBytes(($alerte | ConvertTo-Json -Compress))
Invoke-RestMethod -Method Post -Uri "$Serveur/$Sujet" -Body $corps -ContentType "application/json; charset=utf-8" | Out-Null
Write-Host "Alerte $id publiée sur $Serveur/$Sujet"

# Attente des accusés « recu » et « vu » pendant 60 s, comme l'émetteur (EF-SOS-07 et 08).
$depuis = [int]($emis / 1000) - 5
$client = New-Object System.Net.WebClient
$client.Encoding = [System.Text.Encoding]::UTF8
$recue = $false; $vue = $false
$fin = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $fin -and -not $vue) {
    Start-Sleep -Seconds 2
    # ntfy répond en NDJSON : Invoke-WebRequest le rendrait en octets, WebClient le lit en texte UTF-8.
    $lignes = $client.DownloadString("$Serveur/$Sujet-retour/json?poll=1&since=$depuis") -split "`n"
    foreach ($ligne in $lignes | Where-Object { $_ }) {
        $evt = $ligne | ConvertFrom-Json
        if ($evt.event -ne "message") { continue }
        $accuse = $evt.message | ConvertFrom-Json
        if ($accuse.id -ne $id) { continue }
        $apres = ($accuse.heure - $emis) / 1000
        if ($accuse.type -eq "recu" -and -not $recue) { $recue = $true; Write-Host "Reçue par $($accuse.par) après $apres s" }
        if ($accuse.type -eq "vu" -and -not $vue) { $vue = $true; Write-Host "Vue par $($accuse.par) après $apres s" }
    }
}
if (-not $vue) { Write-Host "Pas de « J'ai vu » en 60 s : l'émetteur proposerait le SMS de secours ou le 112." }
