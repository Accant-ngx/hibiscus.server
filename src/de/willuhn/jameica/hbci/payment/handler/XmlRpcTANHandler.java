/**********************************************************************
 *
 * Copyright (c) 2019 Olaf Willuhn
 * All rights reserved.
 * 
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details. 
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.payment.handler;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import de.willuhn.jameica.hbci.payment.Plugin;
import de.willuhn.jameica.hbci.payment.messaging.TANMessage;
import de.willuhn.jameica.hbci.payment.messaging.TANMessage.TANType;
import de.willuhn.jameica.hbci.payment.messaging.TrustMessageConsumer;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Implementierung eines TAN-Haendlers fuer XML-RPC.
 */
public class XmlRpcTANHandler implements TANHandler
{
  private final static Settings settings = new Settings(XmlRpcTANHandler.class);
  private String config = "default";
  
  private static List<Parameter> templates = new ArrayList<Parameter>();
  static
  {
    templates.add(new Parameter("url","URL des XML-RPC Servers","z.Bsp.: http://server/xmlrpc","http://localhost/xmlrpc"));
    templates.add(new Parameter("method","Name der XML-RPC-Funktion","Der Funktion werden vier Parameter �bergeben.<br/>" +
                                "&nbsp;1. Anzuzeigender Text<br/>" +
                                "&nbsp;2. ID des Kontos (optional)<br/>" +
                                "&nbsp;3. Typ des TAN-Vefahrens (NORMAL, CHIPTAN, PHOTOTAN oder QRTAN)<br/>" + 
                                "&nbsp;4. Payload-Daten f�r die Ermittlung der TAN (bei CHIPTAN der Flickercode, bei PHOTOTAN/QRTAN das Bild als Data-URI z.B. \"data:image/png;base64,...\")<br/>" +
                                "R�ckgabewert der Funktion: Die zu verwendende TAN","hibiscus.getTan"));
    templates.add(new Parameter("username","Username","Optionale Angabe eines Usernamens falls ein HTTP-Login n�tig ist",""));
    templates.add(new Parameter("password","Passwort","Optionale Angabe eines Passwortes falls ein HTTP-Login n�tig ist",""));
  }

  /**
   * @see de.willuhn.jameica.hbci.payment.handler.TANHandler#setConfig(java.lang.String)
   */
  public void setConfig(String config)
  {
    this.config = config;
  }

  /**
   * @see de.willuhn.jameica.hbci.payment.handler.TANHandler#getParameters()
   */
  public Parameter[] getParameters()
  {
    ArrayList parameters = new ArrayList();
    for (int i=0;i<templates.size();++i)
    {
      Parameter param = (Parameter)(templates.get(i)).clone();
      if ("password".equals(param.getId()))
      {
        String value = de.willuhn.jameica.hbci.payment.Settings.getHBCIPassword("tan.haendler.xmlrpc." + config + ".password");
        param.setValue(value != null ? value : param.getDefaultValue());
      }
      else
        param.setValue(settings.getString(config + "." + param.getId(),param.getDefaultValue()));
      parameters.add(param);
    }
    return (Parameter[]) parameters.toArray(new Parameter[parameters.size()]);
  }
  
  /**
   * Liefert den Wert eines Parameters.
   * @param id ID des Parameters.
   * @return Wert des Parameters.
   */
  private String getValue(String id)
  {
    Parameter[] list = getParameters();
    for (int i=0;i<list.length;++i)
    {
      if (id.equals(list[i].getId()))
        return list[i].getValue();
    }
    return null;
  }

  /**
   * @see de.willuhn.jameica.hbci.payment.handler.TANHandler#getTAN(de.willuhn.jameica.hbci.payment.messaging.TANMessage)
   */
  public void getTAN(TANMessage msg) throws Exception
  {
    I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();

    String url      = getValue("url");
    String method   = getValue("method");
    String username = getValue("username");
    String password = de.willuhn.jameica.hbci.payment.Settings.getHBCIPassword("tan.haendler.xmlrpc." + config + ".password");

    if (url == null || url.length() == 0)
      throw new ApplicationException(i18n.tr("Keine URL f�r XML-RPC Aufruf angegeben"));
    
    if (method == null || method.length() == 0)
      throw new ApplicationException(i18n.tr("Kein Funktionsname f�r XML-RPC Aufruf angegeben"));

    Logger.info("starting XML-RPC call to " + url + ", method: " + method);
    TrustMessageConsumer tmc = null;
    try
    {
      if (url.toLowerCase().startsWith("https"))
      {
        Logger.info("using SSL");
        tmc = new TrustMessageConsumer();
        Application.getMessagingFactory().registerMessageConsumer(tmc);
      }

      XmlRpcClientConfigImpl clientConfig = new XmlRpcClientConfigImpl();
      clientConfig.setServerURL(new URL(url));

      if (username != null && username.length() > 0)
      {
        Logger.info("http basic auth enabled");
        clientConfig.setBasicUserName(username);
      }
      if (password != null && password.length() > 0) clientConfig.setBasicPassword(password);

      // Client erzeugen und Config uebernehmen
      XmlRpcClient client = new XmlRpcClient();
      client.setConfig(clientConfig);

      final Konto konto = msg.getKonto();
      final String id = konto != null ? konto.getID() : "";
      final String text = msg.getText();
      final TANType type = msg.getType() != null ? msg.getType() : TANType.NORMAL;
      
      final String tan = (String) client.execute(method,new String[]{text,id,type.name(),msg.getPayload()});
      msg.setTAN(tan);
    }
    finally
    {
      if (tmc != null)
        Application.getMessagingFactory().unRegisterMessageConsumer(tmc);
    }
  }

  /**
   * @see de.willuhn.jameica.hbci.payment.handler.TANHandler#set(java.lang.String, java.lang.String)
   */
  public void set(String id, String value) throws ApplicationException
  {
    I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();

    if ("url".equals(id) && (value == null || value.length() == 0))
      throw new ApplicationException(i18n.tr("Bitte gib eine URL f�r den XML-RPC Aufruf ein"));
    
    if ("method".equals(id) && (value == null || value.length() == 0))
      throw new ApplicationException(i18n.tr("Bitte gib einen Funktionsnamen f�r den XML-RPC Aufruf ein"));

    if ("password".equals(id))
    {
      de.willuhn.jameica.hbci.payment.Settings.setHBCIPassword("tan.haendler.xmlrpc." + config + ".password",value);
      return;
    }
    settings.setAttribute(config + "." + id,value);
  }

  /**
   * @see de.willuhn.jameica.hbci.payment.handler.TANHandler#getName()
   */
  public String getName()
  {
    return "XML-RPC Handler";
  }
}
