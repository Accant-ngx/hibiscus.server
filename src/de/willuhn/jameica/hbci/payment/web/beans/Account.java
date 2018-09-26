/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci.payment.web.beans;

import java.rmi.RemoteException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.willuhn.datasource.pseudo.PseudoIterator;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.hbci.MetaKey;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.SynchronizeOptions;
import de.willuhn.jameica.hbci.payment.Plugin;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.server.BPDUtil;
import de.willuhn.jameica.hbci.server.BPDUtil.Query;
import de.willuhn.jameica.hbci.server.BPDUtil.Support;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.webadmin.annotation.Request;
import de.willuhn.jameica.webadmin.annotation.Response;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Controller fuer ein einzelnes Konto.
 */
public class Account
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();

  @Request
  private HttpServletRequest request = null;
  
  @Response
  private HttpServletResponse response = null;
  
  private Konto konto = null;
  
  /**
   * Liefert das aktuell geladene Konto.
   * @return das aktuell geladene Konto.
   */
  public Konto getAccount()
  {
    return this.konto;
  }
  
  /**
   * Liefert eine Liste der letzten Umsaetze.
   * @return Liste der letzten Umsaetze.
   * @throws RemoteException
   */
  public List<Umsatz> getUmsaetze() throws RemoteException
  {
    DBIterator it = this.konto.getUmsaetze(HBCIProperties.UMSATZ_DEFAULT_DAYS);
    it.setLimit(500);
    return PseudoIterator.asList(it);
  }
  
  /**
   * Liefert eine Liste der letzten Protokoll-Eintraege.
   * @return Liste der letzten Protokoll-Eintraege.
   * @throws RemoteException
   */
  public List<Protokoll> getProtokoll() throws RemoteException
  {
    DBIterator it = this.konto.getProtokolle();
    it.setLimit(200);
    return PseudoIterator.asList(it);
  }

  /**
   * Action zum Laden des Kontos.
   * @throws Exception
   */
  public void load() throws Exception
  {
    String id = this.request.getParameter("id");
    if (id == null || id.length() == 0)
      throw new ApplicationException(i18n.tr("Kein Konto angegeben"));

    this.konto = (Konto) Settings.getDBService().createObject(Konto.class,id);
  }
  
  /**
   * Action zum Loeschen eines Kontos und aller zugeordneten Auftraege.
   * @throws Exception
   */
  public void delete() throws Exception
  {
    try
    {
      this.konto.delete();
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Konto und alle zugeordneten Ums�tze/Auftr�ge gel�scht"),StatusBarMessage.TYPE_SUCCESS));
      response.sendRedirect("accounts.html");
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
    }
  }
  
  /**
   * Aktion zum Speichern der Einstellungen.
   * @throws RemoteException
   */
  public void store() throws RemoteException
  {
    SynchronizeOptions options = this.getOptions();
    options.setSyncSaldo(request.getParameter("saldo") != null);
    options.setSyncKontoauszuege(request.getParameter("umsatz") != null);
    options.setSyncSepaDauerauftraege(request.getParameter("sepadauer") != null);
    options.setSyncAuslandsUeberweisungen(request.getParameter("foreign") != null);
    options.setSyncSepaLastschriften(request.getParameter("sepalast") != null);
    options.setSyncKontoauszuegePdf(request.getParameter("kontoauszug") != null);

    boolean camt = request.getParameter("camt") != null;
    MetaKey.UMSATZ_CAMT.set(konto,Boolean.toString(camt));
    Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Synchronisierungsoptionen gespeichert"),StatusBarMessage.TYPE_SUCCESS));
  }
  
  /**
   * Liefert die Synchronisationseinstellungen fuer das Konto.
   * @return Synchronisationseinstellungen fuer das Konto.
   * @throws RemoteException
   */
  public SynchronizeOptions getOptions() throws RemoteException
  {
    return new SynchronizeOptions(this.konto);
  }
  
  /**
   * Liefert true, wenn der Umsatzabruf per CAMT fuer das Konto aktiv ist.
   * @return true, wenn der Umsatzabruf per CAMT fuer das Konto aktiv ist.
   * @throws RemoteException
   */
  public boolean isUseCamt() throws RemoteException
  {
    return KontoUtil.useCamt(this.konto,false);
  }
  
  /**
   * Prueft, ob das Konto ueberhaupt CAMT unterstuetzt.
   * @return true, wenn das Konto CAMT unterstuetzt.
   * @throws RemoteException
   */
  public boolean isSupportsCamt() throws RemoteException
  {
    Support support = BPDUtil.getSupport(this.konto,Query.UmsatzCamt);
    return support != null && support.isSupported();
  }

}
