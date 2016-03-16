/**********************************************************************
 * $Source: /cvsroot/hibiscus/hibiscus.server/src/de/willuhn/jameica/hbci/payment/HBCICallbackServer.java,v $
 * $Revision: 1.1 $
 * $Date: 2011/11/12 15:09:59 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn software & services
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.payment;

import java.util.Date;

import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.HBCIPassport;

import de.willuhn.jameica.hbci.AbstractHibiscusHBCICallback;
import de.willuhn.jameica.hbci.HBCICallbackSWT;
import de.willuhn.jameica.hbci.passport.PassportHandle;
import de.willuhn.jameica.hbci.payment.messaging.TANMessage;
import de.willuhn.jameica.hbci.payment.web.beans.PassportsPinTan;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Nachricht;
import de.willuhn.jameica.hbci.synchronize.SynchronizeSession;
import de.willuhn.jameica.hbci.synchronize.hbci.HBCISynchronizeBackend;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.services.BeanService;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;

/**
 * Wir ueberschreiben den Hibiscus-Callback, um alle HBCI-Aufrufe ueber uns zu leiten.
 */
public class HBCICallbackServer extends AbstractHibiscusHBCICallback
{
  private HBCICallback parent = null;

  /**
   * ct.
   */
  public HBCICallbackServer()
  {
    if (!Application.inServerMode())
      this.parent = new HBCICallbackSWT();
    else
      this.parent = new HBCICallbackConsole();
    Logger.info("parent hbci callback: " + this.parent.getClass().getName());
  }
  
  /**
   * @see org.kapott.hbci.callback.HBCICallback#log(java.lang.String, int, java.util.Date, java.lang.StackTraceElement)
   */
  public void log(String msg, int level, Date date, StackTraceElement trace)
  {
    switch (level)
    {
      case HBCIUtils.LOG_DEBUG2:
      case HBCIUtils.LOG_DEBUG:
        Logger.debug(msg);
        break;

      case HBCIUtils.LOG_INFO:
        Logger.info(msg);
        break;

      case HBCIUtils.LOG_WARN:
        // Die logge ich mit DEBUG - die nerven sonst
        if (msg != null && msg.startsWith("konnte folgenden nutzerdefinierten Wert nicht in Nachricht einsetzen:"))
        {
          Logger.debug(msg);
          break;
        }
        if (msg != null && msg.matches("Algorithmus .* nicht implementiert"))
        {
          Logger.debug(msg);
          break;
        }
        Logger.warn(msg);
        break;

      case HBCIUtils.LOG_ERR:
        Logger.error(msg + " " + trace.toString());
        break;

      default:
        Logger.warn("(unknown log level " + level + "):" + msg);
    }
  }


  /**
   * @see org.kapott.hbci.callback.HBCICallback#callback(org.kapott.hbci.passport.HBCIPassport, int, java.lang.String, int, java.lang.StringBuffer)
   */
  public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData)
  {
    // Ueberschrieben, um die Passwort-Abfragen abzufangen und die Werte
    // zu speichern.
    switch (reason) {
    
      ///////////////////////////////////////////////////////////////
      // PIN/TAN - reichen wir an die PinTanBean durch
      case NEED_COUNTRY:
      case NEED_BLZ:
      case NEED_HOST:
      case NEED_PORT:
      case NEED_FILTER:
      case NEED_USERID:
      case NEED_CUSTOMERID:
        
        String value = (String) PassportsPinTan.SESSION.get(new Integer(reason));
        if (value == null || value.length() == 0)
        {
          Logger.warn("PIN/TAN: have no valid value for callback reason: " + reason);
          retData.replace(0,retData.length(),"");
        }
        else
        {
          Logger.info("applying callback reason " + reason + " via pintan session");
          retData.replace(0,retData.length(),value);
        }
        return;
        
      case NEED_PT_SECMECH:
        Logger.info("GOT PIN/TAN secmech list: " + msg + " ["+retData.toString()+"]");
        retData.replace(0,retData.length(),Settings.getPinTanSecMech(passport,retData.toString()));
        return;
        
      case NEED_PT_TAN:
        Logger.info("sending TAN message");
        
        BeanService service = Application.getBootLoader().getBootable(BeanService.class);
        SynchronizeSession session = service.get(HBCISynchronizeBackend.class).getCurrentSession();
        Konto konto = session != null ? session.getKonto() : null;
        
        TANMessage tm = new TANMessage(msg, passport, konto);
        Application.getMessagingFactory().sendSyncMessage(tm);
        String tan = tm.getTAN();
        if (tan == null || tan.length() == 0)
          throw new HBCI_Exception("No TAN-handler specified or empty TAN returned");
          
        Logger.info("got TAN message response, sending to institute");
        retData.replace(0,retData.length(),tan);
        return;
      ///////////////////////////////////////////////////////////////
        
        
      case NEED_CONNECTION:
      case CLOSE_CONNECTION:
        // Ueberschrieben, weil wir im Server-Mode davon ausgehen,
        // dass eine Internetverbindung verfuegbar ist
        return;
        
      case NEED_PASSPHRASE_LOAD:
      case NEED_PASSPHRASE_SAVE:
      case NEED_SOFTPIN:
      case NEED_PT_PIN:
        String pw = Settings.getHBCIPassword(passport,reason);
        if (pw == null || pw.length() == 0)
          throw new RuntimeException("no password/pin found for passport " + passport.getClass().getName());

        // Wir haben ein gespeichertes Passwort. Das nehmen wir.
        retData.replace(0,retData.length(),pw);
        return;

      // Implementiert, weil die Console-Impl Eingaben von STDIN erfordern
      case HAVE_INST_MSG:
        try
        {
          Nachricht n = (Nachricht) de.willuhn.jameica.hbci.Settings.getDBService().createObject(Nachricht.class,null);
          n.setBLZ(passport.getBLZ());
          n.setNachricht(msg);
          n.setDatum(new Date());
          n.store();
        }
        catch (Exception e)
        {
          Logger.error("unable to store system message",e);
        }
        return;

      case NEED_INFOPOINT_ACK:
        QueryMessage qm = new QueryMessage(msg,retData);
        Application.getMessagingFactory().getMessagingQueue("hibiscus.infopoint").sendSyncMessage(qm);
        retData.replace(0,retData.length(),qm.getData() == null ? "" : "false");
        return;

      case HAVE_IBAN_ERROR:
      case HAVE_CRC_ERROR:
        Logger.error("IBAN/CRC error: " + msg+ " ["+retData.toString()+"]: "); // Muesste ich mal noch behandeln
        return;

      case WRONG_PIN:
        Logger.error("detected wrong PIN: " + msg+ " ["+retData.toString()+"]: ");
        return;

      case USERID_CHANGED:
        Logger.info("got changed user/account data (code 3072) - saving in persistent data for later handling");
        ((AbstractHBCIPassport)passport).setPersistentData(PassportHandle.CONTEXT_CONFIG,retData.toString());
        return;
        
      case HBCICallback.NEED_CHIPCARD:
        Logger.debug("callback: need chipcard");
        return;
      case HBCICallback.HAVE_CHIPCARD:
        Logger.debug("callback: have chipcard");
        return;
        
      case HBCICallback.NEED_HARDPIN:
      case HBCICallback.HAVE_HARDPIN:
        throw new HBCI_Exception("hard pin not allowed in payment server");

      case HBCICallback.NEED_REMOVE_CHIPCARD:

        // Implementiert, weil die Console-Impl Eingaben von STDIN erfordern
      case HAVE_ERROR:
        Logger.error("NOT IMPLEMENTED: " + msg+ " ["+retData.toString()+"]: ");
        throw new HBCI_Exception("reason not implemented");
    }
    parent.callback(passport, reason, msg, datatype, retData);
  }
  
  /**
   * @see de.willuhn.jameica.hbci.AbstractHibiscusHBCICallback#status(java.lang.String)
   */
  protected void status(String text)
  {
    Logger.info(text);
  }
}
