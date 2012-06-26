/*
 *	Copyright 2010,2011 Ippon Technologies 
 *  
 *	This file is part of Web Integration Portlet (WIP).
 *	Web Integration Portlet (WIP) is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	Web Integration Portlet (WIP) is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public License
 *	along with Web Integration Portlet (WIP).  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.ippon.wip.portlet;

import fr.ippon.wip.config.WIPConfiguration;
import fr.ippon.wip.config.WIPConfigurationDAO;
import fr.ippon.wip.config.WIPConfigurationDAOFactory;
import fr.ippon.wip.http.HttpExecutor;
import fr.ippon.wip.http.Request;
import fr.ippon.wip.http.Response;
import fr.ippon.wip.http.hc.HttpClientExecutor;
import fr.ippon.wip.state.PortletWindow;
import fr.ippon.wip.state.ResponseStore;
import fr.ippon.wip.util.WIPUtil;

import javax.portlet.*;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * WIPortlet enables web application integration within a portlet. It override
 * the processAction, render and serveResource methods of GenericPortlet.
 * 
 * Uses an instance of HttpExecutor to process remote HTTP request/response
 * 
 * @author Anthony Luce
 * @author Quentin Thierry
 * @author François Prot
 */
public class WIPortlet extends GenericPortlet {

	private static final Logger LOG = Logger.getLogger(WIPortlet.class.getName());

	// Session attribute and request parameter keys
	public static final String WIP_REQUEST_PARAMS_PREFIX_KEY = "WIP_";
	public static final String LINK_URL_KEY = "WIP_LINK_URL";
	public static final String METHOD_TYPE = "WIP_METHOD";
	public static final String RESOURCE_TYPE_KEY = "WIP_RESOURCE_TYPE";
	public static final String URL_CONCATENATION_KEY = "WIP_URL_CONCATENATION";

	// Class attributes
	private WIPConfigurationDAO wipConfigurationDAO;
	private HttpExecutor executor;

	// file handler for logging
	private Handler fileHandler;
	
	/**
	 * Initialize configuration and create an instance of HttpExecutor
	 * 
	 * @param config
	 *            Configuration from portlet.xml
	 * @throws PortletException
	 */
	@Override
	public void init(PortletConfig config) throws PortletException {
		super.init(config);
		
		try {
			fileHandler = new FileHandler("%h/transformers.log", true);
			fileHandler.setFormatter(new SimpleFormatter());
			Logger.getLogger("fr.ippon.wip.transformers").addHandler(fileHandler);
			Logger.getLogger("fr.ippon.wip.http.hc").addHandler(fileHandler);
			Logger.getLogger("org.apache.http.impl.client.cache").addHandler(fileHandler);
			
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		int responseStoreMaxEntries = Integer.parseInt(config.getInitParameter("RESPONSE_STORE_MAX_ENTRIES"));
		ResponseStore.getInstance().setMaxEntries(responseStoreMaxEntries);
		
		wipConfigurationDAO = WIPConfigurationDAOFactory.getInstance().getXMLInstance();
		executor = new HttpClientExecutor();
	}

	/**
	 * Retrieve the portlet configuration, creating it if necessary.
	 * 
	 * @Todo: their must be a clever way to initiate the configurations
	 *        preferences, but it doesn't seem possible to access portlet
	 *        preferences in the process of an HttpSessionListener.
	 *
	 * @param request
	 * @return the portlet configuration
	 */
	private WIPConfiguration getOrCreateConfiguration(PortletRequest request) {
		// check if the configuration is already saved in the session
		WIPConfiguration configuration = WIPUtil.extractConfiguration(request);
		if (configuration != null)
			return configuration;

		// retrieve the configuration name associated to the portlet preferences
		PortletPreferences preferences = request.getPreferences();
		String configurationName = preferences.getValue(Attributes.CONFIGURATION_NAME.name(), WIPConfigurationDAO.DEFAULT_CONFIG_NAME);
		configuration = wipConfigurationDAO.read(configurationName);

		// update the session with the configuration
		PortletSession session = request.getPortletSession();
		session.setAttribute(Attributes.CONFIGURATION.name(), configuration);
		return configuration;
	}

	/**
	 * Processes requests in the RENDER phase for the VIEW portlet mode
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@Override
	protected void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		request.getPreferences();
		WIPConfiguration wipConfig = getOrCreateConfiguration(request);

		PortletWindow windowState = PortletWindow.getInstance(request);
		Response wipResponse = null;
		UUID uuid = windowState.getResponseID();
		// A request has just been processed in the ACTION phase
		if (uuid != null) {
			// Get response from store & send it
			wipResponse = ResponseStore.getInstance().remove(uuid);
			windowState.setResponseID(null);
		}

		// If no pending response, create a new request
		if (wipResponse == null) {
			String requestUrl = windowState.getCurrentURL();
			Request wipRequest = new Request(requestUrl, Request.HttpMethod.GET, Request.ResourceType.HTML, null);

			// TODO: copy global parameters from PortletRequest ?
			
			// Execute request
			wipResponse = executor.execute(wipRequest, request, response);
		}
		// Set Portlet title
		response.setTitle(wipConfig.getPortletTitle());

		// Check if authentication is requested by remote host
		if (windowState.getRequestedAuthSchemes() != null) {
			// Redirecting to the form
			String location = Pages.AUTH.getPath();
			PortletRequestDispatcher portletRequestDispatcher = getPortletContext().getRequestDispatcher(location);
			portletRequestDispatcher.include(request, response);
		} else {
			// Print content
			wipResponse.printResponseContent(request, response, windowState.isAuthenticated());
		}
	}

	/**
	 * Processes request in the ACTION phase
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@Override
	public void processAction(ActionRequest request, ActionResponse response) throws PortletException, IOException {
		WIPConfiguration wipConfig = WIPUtil.extractConfiguration(request);

		// If in edit mode, delegates processing to WIPEdit
		if (request.getPortletMode().equals(PortletMode.EDIT)) {
			PortletSession session = request.getPortletSession();

			String configurationName = request.getParameter(Attributes.ACTION_SELECT.name());
			if (StringUtils.isEmpty(configurationName))
				return;

			WIPConfiguration configuration = wipConfigurationDAO.read(configurationName);
			session.setAttribute(Attributes.CONFIGURATION.name(), configuration);

			try {
				request.getPreferences().setValue(Attributes.CONFIGURATION_NAME.name(), configurationName);
				request.getPreferences().store();

			} catch (ReadOnlyException e) {
				e.printStackTrace();
			} catch (ValidatorException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			PortletWindow.clearInstance(request);
			return;
		}
		
		// If request comes from authentication form, process credentials and go
		// in RENDER phase
		if (request.getParameter("auth") != null && request.getParameter(WIPortlet.LINK_URL_KEY) == null) {
			manageAuthentication(request, response);
			return;
		}

		Request wipRequest = new Request(request);
		Response wipResponse = executor.execute(wipRequest, request, response);

		// Check if remote URI must be proxied
		if (!wipConfig.isProxyURI(wipResponse.getUrl())) {
			// Redirect to remote URI without proxying
			try {
				response.sendRedirect(wipResponse.getUrl());
			} finally {
				wipResponse.dispose();
			}
		} else {
			// Store response for future usage
			UUID uuid = ResponseStore.getInstance().store(wipResponse);
			// Check if content must be rendered in the portlet or as an
			// attachment
			if (wipResponse.isHtml()) {
				// Update state & let the portlet render
				PortletWindow windowState = PortletWindow.getInstance(request);
				windowState.setResponseID(uuid);
				windowState.setCurrentURL(wipResponse.getUrl());
			} else {
				// Redirect to ResourceServlet
				response.sendRedirect(request.getContextPath() + "/ResourceHandler?&uuid=" + uuid.toString());
			}
		}
	}

	/**
	 * Processes requests in RESOURCE phase
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@Override
	public void serveResource(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
		// Create request
		Request wipRequest = new Request(request);

		// Execute request
		Response wipResponse = executor.execute(wipRequest, request, response);

		// Print content
		wipResponse.printResponseContent(request, response, false);
	}

	/**
	 * Processes requests in RENDER phase when portlet mode is EDIT
	 * 
	 * Controller that dispatches requests ot the appropriate JSP
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@Override
	protected void doEdit(RenderRequest request, RenderResponse response) throws PortletException, IOException {
		PortletRequestDispatcher portletRequestDispatcher = getPortletContext().getRequestDispatcher(Pages.SELECT_CONFIG.getPath());
		portletRequestDispatcher.include(request, response);
	}

	/**
	 * Releases resources on portlet un-deploy
	 */
	@Override
	public void destroy() {
		super.destroy();
		executor.destroy();
		fileHandler.close();
	}

	private void manageAuthentication(ActionRequest actionRequest, ActionResponse actionResponse) {
		// Login or logout ?
		if (actionRequest.getParameter("auth").equals("login")) {
			// Registering user login & password in session
			executor.login(actionRequest.getParameter("login"), actionRequest.getParameter("password"), actionRequest);
		} else if (actionRequest.getParameter("auth").equals("logout")) {
			// Logout the user
			executor.logout(actionRequest);
		}
	}
}
