/*
 *	Copyright 2010,2011 Ippon Technologies 
 *  
 *	This file is part of Wip Portlet.
 *	Wip Portlet is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	Wip Portlet is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with Wip Portlet.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.ippon.wip.rewriters;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.portlet.PortletResponse;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;

import fr.ippon.wip.portlet.WIPortlet;

/**
 * This class contains the commons methods of all WIP rewriters
 * 
 * @author Anthony Luce
 * @author Quentin Thierry
 * 
 */
public class WIPRewriter {

	/**
	 * The URI of the page of the distant application displayed in the portlet,
	 * used to resolve relative URLs
	 */
	private static URI currentUri;

	/**
	 * A constructor who will build the current URI from a given string
	 * 
	 * @param currentUrl
	 *            The String representation of the current URI
	 */
	public WIPRewriter(String currentUrl) {
		try {
			currentUri = new URI(currentUrl);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * A method to transform a given URL into its absolute form
	 * 
	 * @param link
	 *            The URL to make absolute
	 * @return The absolute form of the given URL
	 */
	public String toAbsolute(String link) {
		try {
			// Try to build an URL to check if protocol and host are specified
			@SuppressWarnings("unused")
			URL tmp = new URL(link);
			// If no error is sent, the URL doesn't need to be rewrited
			return link;
		} catch (MalformedURLException e) {
			// An error occured : the URL is relative and need to be resolved

			// CUSTOM -----------------------------
			int i = link.indexOf("{");
			if (i > -1) return link.substring(i);
			//-------------------------------------
			
			String tmpLink = link.replaceAll(" ", "%20");
			URI abs = currentUri.resolve(tmpLink);
			
			return abs.toString();
		}
	}

	/**
	 * Rewrite an URL into an absolute one
	 * 
	 * @param src
	 *            The URL as a String
	 * @return The given URL, as a String, in its absolute form
	 */
	public String rewriteUrl(String src) {
		return toAbsolute(src);
	}

	/**
	 * Rewrite a resource into a ResourceURL, to make it load through the portal
	 * 
	 * @param src The URL of the resource as a String
	 * @param response An empty PortletResponse used to create a ResourceURL
	 * @param type A String speifiying the type of the resource ( JS | CSS | other )
	 * @return A ResourceURL in its String representation, containing the
	 * 		required informations to recover the resource in the
	 *      serveResource method of the WIPortlet class
	 */
	public String rewriteResource(String src, PortletResponse response,	String type) {
		ResourceURL rUrl = null;
		String ret;
		
		// Creating the ResourceURL
		if (response instanceof RenderResponse)
			rUrl = ((RenderResponse) response).createResourceURL();
		else if (response instanceof ResourceResponse)
			rUrl = ((ResourceResponse) response).createResourceURL();
		
		if (rUrl != null) {
			// Setting parameters
			Map<String, String[]> parameters = new HashMap<String, String[]>();
			String[] tab1 = {type};
			String[] tab2 = {toAbsolute(src)};
			parameters.put(WIPortlet.RESOURCE_TYPE_KEY, tab1);
			parameters.put(WIPortlet.RESOURCE_URL_KEY, tab2);
			rUrl.setParameters(parameters);
			ret = rUrl.toString();
		} else {
			ret = rewriteUrl(src);
		}
		return ret + "&"+WIPortlet.URL_CONCATENATION_KEY+"=";
	}

}