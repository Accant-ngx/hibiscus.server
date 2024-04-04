# Hibiscus Server

> Der Hibiscus Server übernimmt für Dich vollautomatisch und zeitgesteuert das Abrufen neuer Umsätze und Kontoauszüge.

<h2>1. Download</h2>

> ℹ️ Das Download-Archiv [...] enthält ein vorkonfiguriertes Komplettsystem inclusive aller nötigen Komponenten und Plugins. Ein separater Download von Jameica oder Hibiscus ist nicht notwendig. Der Server ist für den Zugriff auf eine MySQL-Datenbank auf "localhost" vorkonfiguriert. Weitere Informationen findest Du in der Installationsanleitung.

Download our tested release of Hibiscus Server: <br>
https://github.com/Accant-ngx/hibiscus.server/releases/latest

..Or download latest version from official website: <br> 
https://www.willuhn.de/products/hibiscus-server/download.php

<h2>2. Installation</h2>

> Source: https://www.willuhn.de/products/hibiscus-server/install.php <br>
> © 2024 Olaf Willuhn

<h3>Systemvoraussetzungen</h3>

<table class="table">
<tbody><tr>
  <td>Betriebssystem</td>
  <td>
    <ul>
      <li>Linux (x86 oder x86_64) oder</li>
      <li>Windows</li>
    </ul>
  </td>
</tr>
<tr>
  <td>Datenbank</td>
  <td>
    <ul>
      <li>MySQL oder</li>
      <li>H2 (embedded Datenbank, keine Installation nötig)</li>
    </ul>
  </td>
</tr>
<tr>
  <td>Java-Version</td>
  <td>
    <ul>
      <li>Java 1.8 oder höher</li>
    </ul>
  </td>
</tr>
</tbody></table>

<h3>Installation</h3>
<ol>
<li class="mb-4">Entpacke das Archiv auf Deinem Server.</li>
<li class="mb-4">Wechsle in das erstellte Verzeichnis "hibiscus-server".</li>
<li class="mb-4">
  Öffne die Datei "cfg/de.willuhn.jameica.hbci.rmi.HBCIDBService.properties"
  in einem Texteditor und passe die Zugangsdaten zur Datenbank an:

<pre><code>database.driver.mysql.jdbcurl=jdbc\:mariadb\://&lt;hostname&gt;\:3306/...
database.driver.mysql.jdbcdriver=org.mariadb.jdbc.Driver
database.driver.mysql.username=&lt;username&gt;
database.driver.mysql.password=&lt;password&gt;</code></pre>

  Gib statt "&lt;hostname&gt;" den Hostnamen der Datenbank (z.Bsp. "localhost" wenn sich Server und Datenbank auf dem selben Rechner befinden) ein.
  Passe außerdem Username und Passwort an.
  
  <div class="alert alert-info">
    <i class="fa fa-info"></i>
    Wenn Du keine externe MySQL-Datenbank nutzen möchtest, dann lösche einfach diese
    Konfigurationsdatei. Der Server wird beim ersten Start automatisch eine
    verschlüsselte Embedded-Datenbank (H2) erstellen.
    Die 3 folgenden Schritte zum Erstellen der Datenbank, des Benutzers und der Tabellen sind in dem Fall nicht notwendig.
  </div>
  
</li>
<li class="mb-4">
  Erstelle anschließend eine neue MySQL-Datenbank mit dem Namen "hibiscus"
<pre><code>mysql&gt; CREATE DATABASE hibiscus
CHARACTER SET utf8 COLLATE utf8_general_ci;</code></pre>
  <div class="alert alert-light">
    <i class="fa fa-info"></i>
    Achte hierbei auf den Zeichensatz "utf8" - verwende nicht "utf8mb4".
  </div>
</li>
<li class="mb-4">Lege einen Benutzer in der Datenbank an:
<pre><code>mysql&gt; CREATE USER '&lt;username&gt;'@'&lt;hostname&gt;'
IDENTIFIED BY '&lt;password&gt;';
mysql&gt; GRANT ALL ON hibiscus.* TO '&lt;username&gt;'@'&lt;hostname&gt;';
mysql&gt; FLUSH PRIVILEGES;
</code></pre> 

  </li><li class="mb-4">Erstelle nun die Hibiscus-Tabellen
  mit dem beiliegenden SQL-Script "mysql-create.sql" - Du findest es
  im Verzeichnis "plugins/hibiscus/sql":

<pre><code>cd plugins/hibiscus/sql
mysql -u &lt;username&gt; -p -h &lt;hostname&gt; hibiscus &lt; mysql-create.sql</code></pre>
</li>

<li class="mb-4">
  Starte den Server mit folgendem Kommando:
<pre><code>./jameicaserver.sh (Linux)
jameicaserver (Windows)</code></pre>

  <div class="alert alert-info">
    <i class="fa fa-info"></i>
    Starte den Server nicht mit Administrator- bzw. Root-Rechten sondern verwende einen
    unpriviligierten Benutzeraccount.
  </div>
</li>
<li class="mb-4">
  Beim ersten Start des Servers wirst Du zur Vergabe eines neuen Masterpasswortes
  aufgefordert, welches bei allen folgenden Starts benötigt wird. Wenn Du dieses
  Passwort nicht immer manuell eingeben möchtest (z.Bsp. weil der Hibiscus Server
  beim Start des Betriebssystems automatisch geladen werden soll), dann kannst Du
  es auch im Startkommando mit dem Parameter <code>-p &lt;Passwort&gt;</code> angeben.
</li>

<li class="mb-4">
  Öffne das Webfrontend des Servers in einem Browser:
  
  <table class="table">
    <thead>
      <tr>
        <th>URL</th>
        <th>Beschreibung</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>https://&lt;server&gt;:8080/webadmin</td>
        <td>Starten und Beenden von Diensten, Log-Ausgaben</td>
      </tr>
      <tr>
        <td>https://&lt;server&gt;:8080/hibiscus</td>
        <td>Webfrontend des Payment-Servers</td>
      </tr>
      <tr>
        <td>https://&lt;server&gt;:8080/sensors</td>
        <td>System-Monitoring</td>
      </tr>
      <tr>
        <td>https://&lt;server&gt;:8080/soap</td>
        <td>Verfügbare SOAP-Webservices</td>
      </tr>
      <tr>
        <td>https://&lt;server&gt;:8080/webadmin/rest.html</td>
        <td>Verfügbare REST-Webservices</td>
      </tr>
      <tr>
        <td>https://&lt;server&gt;:8080/xmlrpc/</td>
        <td>
          Verfügbare XML-RPC-Services. Beachte den Slash "/" am
          Ende der URL. Eine Liste möglicher XML-RPC-Aufrufe findest Du
          im <a href="/wiki/doku.php?id=develop:xmlrpc#xml-rpc-aufrufe_im_detail">Wiki</a>.
        </td>
      </tr>
    </tbody>
  </table>
  Verwende als Benutzername "admin" und als Passwort das beim Serverstart eingegebene Master-Passwort.
</li>
</ol>
