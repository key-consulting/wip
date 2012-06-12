package fr.ippon.wip.portlet;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletSession;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.commons.lang.StringUtils;

import fr.ippon.wip.config.WIPConfiguration;
import fr.ippon.wip.config.WIPConfigurationManager;
import fr.ippon.wip.util.WIPUtil;

public class WIPConfigurationPortlet extends GenericPortlet {

	private WIPConfigurationManager configManager;

	/**
	 * This class will try to build an URL from a string and store an error if
	 * the URL is malformed or empty
	 * 
	 * @param varName
	 *            The name of the variable, used to map errors correctly
	 * @param urlAsString
	 *            The String to convert into a list
	 * @param errors
	 *            The map containing the error messages
	 * @param rb
	 *            The resource bundle used to set the error message
	 * @return The URL built from the given String
	 */
	private URL buildURLIfNotEmpty(String varName, String urlAsString, Map<String, String> errors, ResourceBundle rb) {
		URL result = null;
		if (!urlAsString.equals("")) {
			try {
				result = new URL(urlAsString);
			} catch (MalformedURLException e1) {
				errors.put(varName, rb.getString("wip.errors." + varName + ".malformed"));
			}
		} else {
			errors.put(varName, rb.getString("wip.errors." + varName + ".empty"));
		}
		return result;
	}

	/**
	 * This class will try to build a list of URLs from a string and store an
	 * error if an URL is malformed
	 * 
	 * @param varName
	 *            The name of the variable, used to map errors correctly
	 * @param urlListAsString
	 *            The String to convert into a list
	 * @param errors
	 *            The map containing the error messages
	 * @param rb
	 *            The resource bundle used to set the error message
	 * @return A list of the URLs contained in the given String
	 */
	private List<URL> buildURLList(String varName, String urlListAsString, Map<String, String> errors, ResourceBundle rb, WIPConfiguration wipConfig) {
		List<URL> result = new ArrayList<URL>();
		if (!urlListAsString.equals("")) {
			result = wipConfig.setDomainsFromString(urlListAsString);
		}
		return result;
	}

	/**
	 * Set if necessary the default configuration and page display in the session.
	 * @param request
	 */
	private void checkOrSetSession(PortletRequest request) {
		PortletSession session = request.getPortletSession();

		// check and set if necessary the selected configuration in session
		WIPConfiguration configuration = (WIPConfiguration) session.getAttribute(Attributes.CONFIGURATION.name());
		if (configuration == null)
			session.setAttribute(Attributes.CONFIGURATION.name(), configManager.getDefaultConfiguration());

		// check and set if necessary the configuration page in session
		Pages page = (Pages) session.getAttribute(Attributes.PAGE.name());
		if (page == null)
			session.setAttribute(Attributes.PAGE.name(), Pages.GENERAL_SETTINGS);
	}

	/**
	 * Display the configuration portlet.
	 */
	@Override
	protected void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		checkOrSetSession(request);

		Pages page = (Pages) request.getPortletSession().getAttribute(Attributes.PAGE.name());
		PortletRequestDispatcher portletRequestDispatcher = getPortletContext().getRequestDispatcher(page.getPath());
		portletRequestDispatcher.include(request, response);
	}

	private void handleCaching(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);
		Map<String, String> errors = new HashMap<String, String>();

		// Getting the parameters from the request
		String tmpEnableCache = request.getParameter("enableCache");
		boolean enableCache = true;
		if (tmpEnableCache == null)
			enableCache = false;

		wipConfig.setEnableCache(enableCache);

		if (enableCache) {
			String tmpPageCachePrivate = request.getParameter("pageCachePrivate");
			boolean pageCachePrivate = true;
			if (tmpPageCachePrivate == null)
				pageCachePrivate = false;

			String tmpResourceCachePublic = request.getParameter("resourceCachePublic");
			boolean resourceCachePublic = true;
			if (tmpResourceCachePublic == null)
				resourceCachePublic = false;

			String tmpForcePageCaching = request.getParameter("forcePageCaching");
			boolean forcePageCaching = true;
			if (tmpForcePageCaching == null)
				forcePageCaching = false;

			String tmpForceResourceCaching = request.getParameter("forceResourceCaching");
			boolean forceResourceCaching = true;
			if (tmpForceResourceCaching == null)
				forceResourceCaching = false;

			String tmpPageCacheTimeout = request.getParameter("pageCacheTimeout");
			int pageCacheTimeout = 0;
			if (tmpPageCacheTimeout != null)
				pageCacheTimeout = Integer.parseInt(tmpPageCacheTimeout);

			String tmpResourceCacheTimeout = request.getParameter("resourceCacheTimeout");
			int resourceCacheTimeout = 0;
			if (tmpResourceCacheTimeout != null)
				resourceCacheTimeout = Integer.parseInt(tmpResourceCacheTimeout);

			wipConfig.setPageCachePrivate(pageCachePrivate);
			wipConfig.setResourceCachePublic(resourceCachePublic);
			wipConfig.setForcePageCaching(forcePageCaching);
			wipConfig.setForceResourceCaching(forceResourceCaching);
			wipConfig.setPageCacheTimeout(pageCacheTimeout);
			wipConfig.setResourceCacheTimeout(resourceCacheTimeout);

		}

		// Sending errors to the portlet session
		request.getPortletSession().setAttribute("errors", errors, PortletSession.APPLICATION_SCOPE);

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "caching");
	}

	/**
	 * Handle clipping configuration: get settings in request parameters from
	 * the configuration form. Save them in the portlet configuartion.
	 * 
	 * @param request
	 *            The ActionRequest sent to WIPortlet in edit mode
	 * @param response
	 *            The ActionResponse sent to WIPortlet in edit mode
	 */
	private void handleClipping(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);
		ResourceBundle rb = ResourceBundle.getBundle("content.Language", request.getLocale());
		Map<String, String> errors = new HashMap<String, String>();

		// Getting the parameters from the request
		String clippingType = request.getParameter("clippingType");

		if (clippingType.equals("xpath")) {
			String xPath = request.getParameter("xPath");
			if (xPath.equals("")) {
				errors.put("xPath", rb.getString("wip.errors.xpath.empty"));
			} else {
				wipConfig.setXPath(xPath);
				wipConfig.setClippingType(clippingType);
				wipConfig.save();
			}
		} else if (clippingType.equals("xslt")) {
			String xsltClipping = request.getParameter("xsltClipping");
			wipConfig.setXsltClipping(xsltClipping);
			wipConfig.setClippingType(clippingType);
			wipConfig.save();

		} else {
			wipConfig.setClippingType(clippingType);
			wipConfig.save();
		}

		// Sending errors to the portlet session
		request.getPortletSession().setAttribute("errors", errors, PortletSession.APPLICATION_SCOPE);

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "clipping");
	}

	/**
	 * Handle CSS rewriting configuration: get settings in request parameters
	 * from the configuration form. Save them in the portlet configuartion.
	 * 
	 * @param request
	 *            The ActionRequest sent to WIPortlet in edit mode
	 * @param response
	 *            The ActionResponse sent to WIPortlet in edit mode
	 */
	private void handleCSSRewriting(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);
		Map<String, String> errors = new HashMap<String, String>();

		// Getting the parameters from the request
		String customCss = request.getParameter("customCss");
		String cssRegex = request.getParameter("cssRegex");
		String portletDivId = request.getParameter("portletDivId");

		String tmpEnableCssRetrieving = request.getParameter("enableCssRetrieving");
		boolean enableCssRetrieving = true;
		if (tmpEnableCssRetrieving == null)
			enableCssRetrieving = false;

		String tmpAbsolutePositioning = request.getParameter("absolutePositioning");
		boolean absolutePositioning = true;
		if (tmpAbsolutePositioning == null)
			absolutePositioning = false;

		String tmpAddPrefix = request.getParameter("addPrefix");
		boolean addPrefix = true;
		if (tmpAddPrefix == null)
			addPrefix = false;

		String tmpEnableCssRewriting = request.getParameter("enableCssRewriting");
		boolean enableCssRewriting = true;
		if (tmpEnableCssRewriting == null)
			enableCssRewriting = false;

		// Saving the new configuration
		wipConfig.setCssRegex(cssRegex);
		wipConfig.setAbsolutePositioning(absolutePositioning);
		wipConfig.setAddPrefix(addPrefix);
		wipConfig.setPortletDivId(portletDivId);
		wipConfig.setEnableCssRetrieving(enableCssRetrieving);
		wipConfig.setEnableCssRewriting(enableCssRewriting);
		wipConfig.setCustomCss(customCss);
		wipConfig.save();

		// Sending errors to the portlet session
		request.getPortletSession().setAttribute("errors", errors, PortletSession.APPLICATION_SCOPE);

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "cssrewriting");
	}

	/**
	 * Handle general settings: get settings in request parameters from the
	 * configuration form. Save them in the portlet configuration.
	 * 
	 * @param request
	 *            The ActionRequest sent to WIPortlet in edit mode
	 * @param response
	 *            The ActionResponse sent to WIPortlet in edit mode
	 */
	private void handleGeneralSettings(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);
		ResourceBundle rb = ResourceBundle.getBundle("content.Language", request.getLocale());
		Map<String, String> errors = new HashMap<String, String>();

		// Getting the parameters from the request
		String tmpInitUrl = request.getParameter("initUrl");
		URL initUrl = buildURLIfNotEmpty("initUrl", tmpInitUrl, errors, rb);

		String tmpDomainsToProxy = request.getParameter("domainsToProxy");
		List<URL> domainsToProxy = buildURLList("domainsToProxy", tmpDomainsToProxy, errors, rb, wipConfig);

		String tmpEnableUrlRewriting = request.getParameter("enableUrlRewriting");
		boolean enableUrlRewriting = true;
		if (tmpEnableUrlRewriting == null)
			enableUrlRewriting = false;

		String portletTitle = request.getParameter("portletTitle");

		// Saving the new configuration
		if (initUrl != null)
			wipConfig.setInitUrl(initUrl);
		if (domainsToProxy != null)
			wipConfig.setDomainsToProxy(domainsToProxy);
		if (portletTitle != null)
			wipConfig.setPortletTitle(portletTitle);
		wipConfig.setEnableUrlRewriting(enableUrlRewriting);
		wipConfig.save();

		// Sending errors to the portlet session
		request.getPortletSession().setAttribute("errors", errors, PortletSession.APPLICATION_SCOPE);

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "generalsettings");
	}

	/**
	 * Handle HTML rewriting configuration: get settings in request parameters
	 * from the configuration form. Save them in the portlet configuartion.
	 * 
	 * @param request
	 *            The ActionRequest sent to WIPortlet in edit mode
	 * @param response
	 *            The ActionResponse sent to WIPortlet in edit mode
	 */
	private void handleHtmlRewriting(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);

		// Getting the parameters from the request
		String xsltTransform = request.getParameter("xsltTransform");

		// Saving the new configuration
		wipConfig.setXsltTransform(xsltTransform);
		wipConfig.save();

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "htmlrewriting");
	}

	/**
	 * Handle JS rewriting configuration: get settings in request parameters
	 * from the configuration form. Save them in the portlet configuartion.
	 * 
	 * @param request
	 *            The ActionRequest sent to WIPortlet in edit mode
	 * @param response
	 *            The ActionResponse sent to WIPortlet in edit mode
	 */
	private void handleJSRewriting(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);
		Map<String, String> errors = new HashMap<String, String>();

		// Getting the parameters from the request
		String jsRegex = request.getParameter("jsRegex");

		String tmpJavascriptUrls = request.getParameter("javascriptUrls");
		String[] l1 = tmpJavascriptUrls.split(";");
		List<String> javascriptUrls = Arrays.asList(l1);

		String tmpScriptsToIgnore = request.getParameter("scriptIgnoredUrls");
		String[] l3 = tmpScriptsToIgnore.split(";");
		List<String> scriptsToIgnore = Arrays.asList(l3);

		String tmpScriptsToDelete = request.getParameter("scriptDeletedUrls");
		String[] l4 = tmpScriptsToDelete.split(";");
		List<String> scriptsToDelete = Arrays.asList(l4);

		// Saving the new configuration
		wipConfig.setJsRegex(jsRegex);
		wipConfig.setJavascriptUrls(javascriptUrls);
		wipConfig.setScriptsToIgnore(scriptsToIgnore);
		wipConfig.setScriptsToDelete(scriptsToDelete);
		wipConfig.save();

		// Sending errors to the portlet session
		request.getPortletSession().setAttribute("errors", errors, PortletSession.APPLICATION_SCOPE);

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "jsrewriting");
	}

	private void handleLTPAAuthentication(ActionRequest request, ActionResponse response) {
		// Getting WIPConfig, resource bundle and a map to store errors
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);
		Map<String, String> errors = new HashMap<String, String>();

		// Getting the parameters from the request
		String tmpLtpaSsoAuthentication = request.getParameter("ltpaSsoAuthentication");
		boolean ltpaSsoAuthentication = true;
		if (tmpLtpaSsoAuthentication == null)
			ltpaSsoAuthentication = false;
		String ltpaSecretProviderClassName = request.getParameter("ltpaSecretProviderClassName");
		String credentialProviderClassName = request.getParameter("credentialProviderClassName");

		// Saving the new configuration
		wipConfig.setLtpaSsoAuthentication(ltpaSsoAuthentication);
		wipConfig.setLtpaSecretProviderClassName(ltpaSecretProviderClassName);
		wipConfig.setCredentialProviderClassName(credentialProviderClassName);
		wipConfig.save();

		// Sending errors to the portlet session
		request.getPortletSession().setAttribute("errors", errors, PortletSession.APPLICATION_SCOPE);

		// Sending the page to display to the portlet session
		request.getPortletSession().setAttribute("editPage", "ltpaauth");
	}

	@Override
	public void init() throws PortletException {
		super.init();
		configManager = WIPConfigurationManager.getInstance();
	}

	/**
	 * Process the user action: a configuration can be deleted, selected or saved.
	 */
	@Override
	public void processAction(ActionRequest request, ActionResponse response) throws PortletException, IOException {
		PortletSession session = request.getPortletSession();

		String configName = request.getParameter(Attributes.PAGE.name());
		if (configName != null) {
			session.setAttribute(Attributes.PAGE.name(), Pages.valueOf(configName));
			return;
		}

		configName = request.getParameter(Attributes.ACTION_DELETE.name());
		if(!StringUtils.isEmpty(configName)) {
			configManager.deleteConfiguration(configName);
			return;
		}

		configName = request.getParameter(Attributes.ACTION_SELECT.name());
		if(!StringUtils.isEmpty(configName)) {
			session.setAttribute(Attributes.CONFIGURATION.name(), configManager.getConfiguration(configName));
			session.setAttribute(Attributes.PAGE.name(), Pages.GENERAL_SETTINGS);
			return;
		}
		
		configName = request.getParameter(Attributes.ACTION_SAVE.name());
		if(!StringUtils.isEmpty(configName)) {
			WIPConfiguration configuration = (WIPConfiguration) session.getAttribute(Attributes.CONFIGURATION.name());
			configuration = configManager.createConfiguration(configName, configuration);
			session.setAttribute(Attributes.CONFIGURATION.name(), configuration);
			session.setAttribute(Attributes.PAGE.name(), Pages.GENERAL_SETTINGS);
			return;
		}

		if (request.getParameter("form") != null) {
			switch (Integer.valueOf(request.getParameter("form"))) {
			case 1:
				handleGeneralSettings(request, response);
				break;
			case 2:
				handleClipping(request, response);
				break;
			case 3:
				handleHtmlRewriting(request, response);
				break;
			case 4:
				handleCSSRewriting(request, response);
				break;
			case 5:
				handleJSRewriting(request, response);
				break;
			case 6:
				handleCaching(request, response);
				break;
			case 7:
				handleLTPAAuthentication(request, response);
				break;
			}
		}
	}
}