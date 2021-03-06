/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.web;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.axelor.apps.base.service.BankDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.IrrecoverableService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class InvoiceController {

	@SuppressWarnings("unused")
	private final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	@Inject
	private InvoiceService invoiceService;
	
	@Inject
	private InvoiceRepository invoiceRepo;
	
	/**
	 * Fonction appeler par le bouton calculer
	 *
	 * @param request
	 * @param response
	 * @return
	 */
	public void compute(ActionRequest request, ActionResponse response) {

		Invoice invoice = request.getContext().asType(Invoice.class);

		try{
			invoice = invoiceService.compute(invoice);
			response.setValues(invoice);
		}
		catch(Exception e)  {
			TraceBackService.trace(response, e);
		}
	}

	/**
	 * Fonction appeler par le bouton valider
	 *
	 * @param request
	 * @param response
	 * @return
	 */
	public void validate(ActionRequest request, ActionResponse response) throws AxelorException {

		Invoice invoice = request.getContext().asType(Invoice.class);
		invoice = invoiceRepo.find(invoice.getId());

		try{
			invoiceService.validate(invoice, true);
			response.setReload(true);
		}
		catch(Exception e)  {
			TraceBackService.trace(response, e);
		}
	}

	/**
	 * Fonction appeler par le bouton ventiler
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws AxelorException
	 */
	public void ventilate(ActionRequest request, ActionResponse response) throws AxelorException {

		Invoice invoice = request.getContext().asType(Invoice.class);
		invoice = invoiceRepo.find(invoice.getId());

		try {
			invoiceService.ventilate(invoice);
			response.setReload(true);
		} catch(Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	/**
	 * Passe l'état de la facture à "annulée"
	 * @param request
	 * @param response
	 * @throws AxelorException
	 */
	public void cancel(ActionRequest request, ActionResponse response) throws AxelorException {

		Invoice invoice = request.getContext().asType(Invoice.class);
		invoice = invoiceRepo.find(invoice.getId());

		invoiceService.cancel(invoice);
		response.setFlash(I18n.get(IExceptionMessage.INVOICE_1));
		response.setReload(true);
	}
	
	/**
	 * Function returning both the paymentMode and the paymentCondition
	 * @param request
	 * @param response
	 * @throws AxelorException
	 */

	public void fillPaymentModeAndCondition(ActionRequest request, ActionResponse response) throws AxelorException {
		Invoice invoice = request.getContext().asType(Invoice.class);
		PaymentMode paymentMode = InvoiceToolService.getPaymentMode(invoice);
		PaymentCondition paymentCondition = InvoiceToolService.getPaymentCondition(invoice);
		response.setValue("paymentMode", paymentMode);
		response.setValue("paymentCondition", paymentCondition);
	}
	

	/**
	 * Fonction appeler par le bouton générer un avoir.
	 *
	 * @param request
	 * @param response
	 */
	public void createRefund(ActionRequest request, ActionResponse response) {

		Invoice invoice = request.getContext().asType(Invoice.class);

		try {

			invoice = invoiceRepo.find(invoice.getId());
			Invoice refund = invoiceService.createRefund( invoice );
			response.setReload(true);
			response.setNotify(I18n.get(IExceptionMessage.INVOICE_2));

			response.setView ( ActionView.define( String.format(I18n.get(IExceptionMessage.INVOICE_4), invoice.getInvoiceId() ) )
			.model(Invoice.class.getName())
			.add("form", "invoice-form")
			.add("grid", "invoice-grid")
			.param("forceTitle", "true")
			.context("_showRecord", refund.getId().toString())
			.domain("self.originalInvoice.id = " + invoice.getId())
			.map() );
		}
		catch(Exception e)  {
			TraceBackService.trace(response, e);
		}
	}

	public void usherProcess(ActionRequest request, ActionResponse response) {

		Invoice invoice = request.getContext().asType(Invoice.class);
		invoice = invoiceRepo.find(invoice.getId());

		try {
			invoiceService.usherProcess(invoice);
		}
		catch (Exception e){
			TraceBackService.trace(response, e);
		}
	}

	public void passInIrrecoverable(ActionRequest request, ActionResponse response)  {

		Invoice invoice = request.getContext().asType(Invoice.class);
		invoice = invoiceRepo.find(invoice.getId());

		try  {
			Beans.get(IrrecoverableService.class).passInIrrecoverable(invoice, true);
			response.setReload(true);
		}
		catch(Exception e)  {
			TraceBackService.trace(response, e);
		}
	}

	public void notPassInIrrecoverable(ActionRequest request, ActionResponse response)  {

		Invoice invoice = request.getContext().asType(Invoice.class);
		invoice = invoiceRepo.find(invoice.getId());

		try  {
			Beans.get(IrrecoverableService.class).notPassInIrrecoverable(invoice);
			response.setReload(true);
		}
		catch(Exception e)  {
			TraceBackService.trace(response, e);
		}
	}

	/**
	 * Method to generate invoice as a Pdf
	 */
	@SuppressWarnings("unchecked")
	public void showInvoice(ActionRequest request, ActionResponse response) throws AxelorException {
		Context context = request.getContext();
		ReportSettings reportSetting = null;
		
		if(context.containsKey("_ids") && !ObjectUtils.isEmpty(request.getContext().get("_ids"))) {
			List<Long> ids = Lists.transform((List) request.getContext().get("_ids"), new Function<Object, Long>() {
                @Nullable
                @Override
                public Long apply(@Nullable Object input) {
                    return Long.parseLong(input.toString());
                }
            });
            reportSetting = invoiceService.printInvoices(ids);
		} else if(context.containsKey("id")) {
			reportSetting = invoiceService.printInvoice(request.getContext().asType(Invoice.class), false);
		} else {
			response.setFlash(I18n.get(IExceptionMessage.INVOICE_3));
			return;
		}

		response.setView(ActionView
				.define(reportSetting.getOutputName())
				.add("html", reportSetting.getFileLink()).map());

	}

	@SuppressWarnings("unchecked")
	public void massValidation(ActionRequest request, ActionResponse response) {

		List<Integer> listSelectedInvoice = (List<Integer>) request.getContext().get("_ids");
		if(listSelectedInvoice != null){
			Invoice invoice = null;
			int count = 1;
			for(Integer invoiceId : listSelectedInvoice){
				invoice = invoiceRepo.find(invoiceId.longValue());
				if (invoice.getStatusSelect() != InvoiceRepository.STATUS_DRAFT){
					continue;
				}else{
					try {
						invoiceService.validate(invoice);
					} catch (AxelorException e) {
						TraceBackService.trace(e);
					} finally{
						if (count%10 == 0){
							JPA.clear();
						}
						count ++;
					}
				}
			}
		}
		response.setReload(true);

	}

	@SuppressWarnings("unchecked")
	public void massVentilation(ActionRequest request, ActionResponse response) {

		List<Integer> listSelectedInvoice = (List<Integer>) request.getContext().get("_ids");
		if(listSelectedInvoice != null){
			Invoice invoice = null;
			int count = 1;
			for(Integer invoiceId : listSelectedInvoice){
				invoice = invoiceRepo.find(invoiceId.longValue());
				if (invoice.getStatusSelect() != InvoiceRepository.STATUS_VALIDATED){
					continue;
				}else{
					try {
						invoiceService.ventilate(invoice);
					} catch (AxelorException e) {
						TraceBackService.trace(e);
					} finally{
						if (count%10 == 0){
							JPA.clear();
						}
						count ++;
					}
				}
			}
		}
		response.setReload(true);

	}
	
	//Generate single invoice from several
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void mergeInvoice(ActionRequest request, ActionResponse response)  {
		List<Invoice> invoiceList = new ArrayList<Invoice>();
		List<Long> invoiceIdList = new ArrayList<Long>();
		boolean fromPopup = false;

		if (request.getContext().get("invoiceToMerge") != null){

			if (request.getContext().get("invoiceToMerge") instanceof List){
				//No confirmation popup, invoices are content in a parameter list
				List<Map> invoiceMap = (List<Map>) request.getContext().get("invoiceToMerge");
				for (Map map : invoiceMap) {
					invoiceIdList.add(new Long((Integer)map.get("id")));
				}
			}else{
				//After confirmation popup, invoice's id are in a string separated by ","
				String invoiceIdListStr = (String)request.getContext().get("invoiceToMerge");
				for (String invoiceId : invoiceIdListStr.split(",")) {
					invoiceIdList.add(new Long(invoiceId));
				}
				fromPopup = true;
			}
		}
		
		//Check if company, currency and partner are the same for all selected invoices
		Company commonCompany = null;
		Currency commonCurrency = null;
		Partner commonPartner = null;
		PaymentCondition commonPaymentCondition = null;
		//Useful to determine if a difference exists between payment conditions of all invoices
		boolean existPaymentConditionDiff = false;
		Partner commonContactPartner = null;
		//Useful to determine if a difference exists between contact partners of all purchase orders
		boolean existContactPartnerDiff = false;
		PriceList commonPriceList = null;
		//Useful to determine if a difference exists between price lists of all purchase orders
		boolean existPriceListDiff = false;
		PaymentMode commonPaymentMode = null;
		//Useful to determine if a difference exists between locations of all purchase orders
		boolean existPaymentModeDiff = false;

		Invoice invoiceTemp;
		int count = 1;
		for (Long invoiceId : invoiceIdList) {
			invoiceTemp = JPA.em().find(Invoice.class, invoiceId);
			invoiceList.add(invoiceTemp);
			if(count == 1){
				commonCompany = invoiceTemp.getCompany();
				commonCurrency = invoiceTemp.getCurrency();
				commonPartner = invoiceTemp.getPartner();
				commonPaymentCondition = invoiceTemp.getPaymentCondition();
				commonContactPartner = invoiceTemp.getContactPartner();
				commonPriceList = invoiceTemp.getPriceList();
				commonPaymentMode = invoiceTemp.getPaymentMode();
			}else{
				if (commonCompany != null
						&& !commonCompany.equals(invoiceTemp.getCompany())){
					commonCompany = null;
				}
				if (commonCurrency != null
						&& !commonCurrency.equals(invoiceTemp.getCurrency())){
					commonCurrency = null;
				}
				if (commonPartner != null
						&& !commonPartner.equals(invoiceTemp.getPartner())){
					commonPartner = null;
				}
				if (commonPaymentCondition != null
						&& !commonPaymentCondition.equals(invoiceTemp.getPaymentCondition())){
					commonPaymentCondition = null;
					existPaymentConditionDiff = true;
				}
				if (commonContactPartner != null
						&& !commonContactPartner.equals(invoiceTemp.getContactPartner())){
					commonContactPartner = null;
					existContactPartnerDiff = true;
				}
				if (commonPriceList != null
						&& !commonPriceList.equals(invoiceTemp.getPriceList())){
					commonPriceList = null;
					existPriceListDiff = true;
				}
				if (commonPaymentMode != null
						&& !commonPaymentMode.equals(invoiceTemp.getPaymentMode())){
					commonPaymentMode = null;
					existPaymentModeDiff = true;
				}

			}
			count++;
		}

		StringBuilder fieldErrors = new StringBuilder();
		if (commonCurrency == null){
			fieldErrors.append(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_CURRENCY));
		}
		if (commonCompany == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_COMPANY));
		}
		if (commonPartner == null){
			if (fieldErrors.length() > 0){
				fieldErrors.append("<br/>");
			}
			fieldErrors.append(I18n.get(IExceptionMessage.INVOICE_MERGE_ERROR_PARTNER));
		}

		if (fieldErrors.length() > 0){
			response.setFlash(fieldErrors.toString());
			return;
		}

		//Check if contactPartner or priceList or paymentMode or paymentCondition  or saleOrder are content in parameters
		if (request.getContext().get("contactPartner") != null){
			commonContactPartner = JPA.em().find(Partner.class, new Long((Integer)((Map)request.getContext().get("contactPartner")).get("id")));
		}
		if (request.getContext().get("priceList") != null){
			commonPriceList = JPA.em().find(PriceList.class, new Long((Integer)((Map)request.getContext().get("priceList")).get("id")));
		}
		if (request.getContext().get("paymentMode") != null){
			commonPaymentMode = JPA.em().find(PaymentMode.class, new Long((Integer)((Map)request.getContext().get("paymentMode")).get("id")));
		}
		if (request.getContext().get("paymentCondition") != null){
			commonPaymentCondition = JPA.em().find(PaymentCondition.class, new Long((Integer)((Map)request.getContext().get("paymentCondition")).get("id")));
		}

		if (!fromPopup
				&& (existPaymentConditionDiff || existContactPartnerDiff || existPriceListDiff || existPaymentModeDiff)){
			//Need to display intermediate screen to select some values
			ActionViewBuilder confirmView = ActionView
					.define("Confirm merge invoice")
					.model(Wizard.class.getName())
					.add("form", "customer-invoices-merge-confirm-form")
					.param("popup", "true")
					.param("show-toolbar", "false")
					.param("show-confirm", "false")
					.param("popup-save", "false")
					.param("forceEdit", "true");
			
			if (existContactPartnerDiff){
				confirmView.context("contextContactPartnerToCheck", "true");
				confirmView.context("contextPartnerId", commonPartner.getId().toString());
			}
			if (existPriceListDiff){
				confirmView.context("contextPriceListToCheck", "true");
			}
			if (existPaymentModeDiff){
				confirmView.context("contextPaymentModeToCheck", "true");
			}
			if (existPaymentConditionDiff){
				confirmView.context("contextPaymentConditionToCheck", "true");
			}
			confirmView.context("invoiceToMerge", Joiner.on(",").join(invoiceIdList));

			response.setView(confirmView.map());

			return;
		}
		try{
			Invoice invoice = invoiceService.mergeInvoice(invoiceList,commonCompany, commonCurrency, commonPartner, commonContactPartner, commonPriceList,commonPaymentMode, commonPaymentCondition);
			if (invoice != null){
				//Open the generated invoice in a new tab
				response.setView(ActionView
						.define("Invoice")
						.model(Invoice.class.getName())
						.add("grid", "invoice-grid")
						.add("form", "invoice-form")
						.param("forceEdit", "true")
						.context("_showRecord", String.valueOf(invoice.getId())).map());
				response.setCanClose(true);
			}
		}catch(AxelorException ae){
			response.setFlash(ae.getLocalizedMessage());
		}
	}

	/**
	 * Called on load and in partner, company or payment mode change.
	 * Fill the bank details with a default value.
 	 * @param request
	 * @param response
	 */
	public void fillCompanyBankDetails(ActionRequest request, ActionResponse response) {
		Invoice invoice = request.getContext().asType(Invoice.class);
		PaymentMode paymentMode = invoice.getPaymentMode();
		Company company = invoice.getCompany();
		Partner partner = invoice.getPartner();
		if (company == null) {
			return;
		}
		if (partner != null) {
			partner = Beans.get(PartnerRepository.class).find(partner.getId());
		}
		BankDetails defaultBankDetails = Beans.get(BankDetailsService.class)
				.getDefaultCompanyBankDetails(company, paymentMode, partner);
		response.setValue("companyBankDetails", defaultBankDetails);
	}

}
