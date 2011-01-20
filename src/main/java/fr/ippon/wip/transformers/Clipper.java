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

package fr.ippon.wip.transformers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

import javax.portlet.PortletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import fr.ippon.wip.config.WIPConfiguration;
import fr.ippon.wip.config.WIPConfigurationManager;
import fr.ippon.wip.util.CachedDTD;


/**
 * A class used to process a clipping thanks to a XSLT transformation
 * This class implements the WIPTransformer interface
 * 
 * @author Anthony Luce
 * @author Quentin Thierry
 */
public class Clipper implements WIPTransformer {

	/**
	 * The XSLT stylesheet used to process the clipping
	 */
	private String xsltClipping;

	/**
	 * The instance of the WIPConfiguration
	 */
	private WIPConfiguration wipConfig;

	/**
	 * A basic constructor, getting the WIPConfiguration instance and the stylesheet
	 */
	public Clipper(PortletResponse response) {
		wipConfig = WIPConfigurationManager.getInstance().getConfiguration(response.getNamespace());
		xsltClipping = wipConfig.getXsltClipping();
	}

	/**
	 * This method will be called before the HTMLTransformer's one when a clipping is set,
	 * processing the transformation of the given input keeping only the wanted parts of the content
	 * @param input The input String corresponding to the whole content of the distant page
	 * @return Only the content specified in the configuration
	 * @throws TransformerException 
	 */
	public String transform(String input) throws SAXException, IOException, TransformerException {
		
		// Parsing the content into XHTML
		input = HTMLTransformer.htmlToXhtml(input);

//		try {
//			File f = new File("C:/Users/aluce/Documents/clipper.html");
//			FileWriter writer = new FileWriter(f);
//			BufferedWriter out = new BufferedWriter(writer);
//			out.write(input);
//			out.close();
//		} catch (IOException e) { }
		
		// Performing the XSLT transformation
		InputSource xhtml = new InputSource(new ByteArrayInputStream(input.getBytes()));
		
		// Initilazing the transformer
		TransformerFactory tFactory = TransformerFactory.newInstance();
		StringReader sr = new StringReader(xsltClipping);
		Transformer transformer = tFactory.newTransformer(new StreamSource(sr));
		
		DocumentBuilder db = null;
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (FactoryConfigurationError e) {
			e.printStackTrace();
		}
		db.setEntityResolver(new CachedDTD());
		Document doc = db.parse(xhtml);
		transformer.setErrorListener(new ParserErrorListener());
		
		// Setting parameters used in the stylesheet
		if (wipConfig.getClippingType().equals("xpath")) {
			transformer.setParameter("xpath", wipConfig.getXPath());
		}
		transformer.setParameter("type", wipConfig.getClippingType());
		
		// Processing the transformation
		ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(doc), new StreamResult(resultOutputStream));

		return resultOutputStream.toString();
	}

}