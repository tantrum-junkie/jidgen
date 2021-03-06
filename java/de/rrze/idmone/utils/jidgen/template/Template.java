/*
 * jidgen, developed as a part of the IDMOne project at RRZE.
 * Copyright 2008, RRZE, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors. This
 * product includes software developed by the Apache Software Foundation
 * http://www.apache.org/
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package de.rrze.idmone.utils.jidgen.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.rrze.idmone.utils.jidgen.Messages;



/**
 * This class builds strings out of specified
 * data and a given template string.
 * The template string describes what the generated
 * string should "look like" in reference to the
 * given data.
 * 
 * Because this data will most likely be given through a
 * call on the command line, we will try to not use characters
 * that already have a function in the BASH shell in our 
 * template definition string.<br />
 * This helps avoiding nasty escape wars on the command line :)
 * 
 * The following explains the very simple description language 
 * this template class understands. The elements are grouped by
 * function and each has a very easy example.
 * 
 * <pre>
 * Element delimiter: 				:
 * Random indicator(exactly one): 	+
 * Fixed string delimiter:			=
 * Range declaration:				{Numbers}
 * 
 * -> Use string from variable from/to char as in substring(start, end, variable)
 * 		=> 1n2:5a6 or n1,2:a5,6
 * -> Choose one random character from the given character sequence
 *		=> n+:a+
 * -> Use x characters from then beginning/end of the variable
 * 		=> 1n:5v (beginning) / n1:v5 (end)
 * -> Use a fixed string
		=> =myPrefixWithSpecialChars:123+=:1a3:
 * </pre>
 * 
 * 
 * As a reference here are most of those characters we will try
 * to avoid (source <a href="http://www.osxfaq.com/tips/unix-tricks/week83/monday.ws">http://www.osxfaq.com/tips/unix-tricks/week83/monday.ws</a>:<br />
 * 
 * <pre>
 * #        introduce a comment
 * ;        command separator
 * {...}    signifies a command block
 * (...)    forces execution in a sub-shell
 * &&       logical and (placed between commands)
 * ||       logical or (placed between commands)
 * 
 * 
 * ~        expanded to current user's home directory
 * ~user    expanded to user's home directory 
 * /        directory/filename separator
 * 
 * $var     expands variable 'var'
 * `...`    executes a command and substitutes the output
 * $(...)   Bash's preferred syntax for command substitution
 * $((...)) evaluates an integer arithmetic expression
 * ((...))  evaluates an integer arithmetic in a condition  
 * 
 * '        strong quote
 * "        weak quote
 * \        escape next character (cancel special meaning)
 * 
 * *        wildcard
 * [...]    character set wildcard (edit-unrza249: can be used!)
 * ?        single character wildcard
 * 
 * &        forces command to be executed in the background
 * <        redirect stdin
 * >        redirect stdout
 * |        pipe
 * !        pipeline logical NOT
 * </pre>
 * 
 * @author unrza249
 *
 */
public class Template {

	/**
	 *  The class logger
	 */
	private static final Log logger = LogFactory.getLog(Template.class);
	
	/**
	 * A prefix string for all options parameters
	 * and the marker for the template string in one.
	 */
	private String prefix = "T";
		
	/**
	 * The template string
	 */
	private String template;

	/**
	 * The list of element objects generated by parsing the template string
	 */
	private ArrayList<IElement> elements;
	
	/**
	 * The data used to generate an id out of the template
	 * string.
	 */
	private HashMap<String,String> data = new HashMap<String,String>();

	/**
	 * Whether at least one of the element objects
	 * claims to have at least one more alternate result
	 * left to return.
	 */
	private boolean hasAlternatives = true;
	
	/**
	 * Whether the elements array should be rebuild
	 * by re-parsing the template string.
	 * <b>used only internally</b>
	 */
	private boolean updateElements = true;
	
	/**
	 * Whether the elements that request external data
	 * should be fed the current data from the stored
	 * data array.
	 * <b>used only internally</b>
	 */
	private boolean updateData = true;
	
	/**
	 * Pointer to the next resover element to activate.
	 * This is automatically set to the first active
	 * resolver element found by the buildString() loop.
	 * <b>used only internally</b>
	 */
	private IElement nextResolver = null;
	
	
	/**
	 * simple constructor
	 */
	public Template() {
		// init the data map with the predefined defaults
		this.data.putAll(Template.getPredefinedData(this.prefix));
	}
	
	/**
	 * constructor with template string
	 * 
	 * @param template
	 * 			the template string
	 */
	public Template(String template) {
		this();
		this.template = template;
	}
	
	/**
	 * constructor with data array
	 * 
	 * @param data
	 * 			the data array
	 */
	public Template(HashMap<String,String> data) {
		this();
		this.updateData(data);
		
		// set template string from data array if possible
		if (this.data.containsKey(this.prefix)) {
			this.setTemplate(this.data.get(this.prefix));
		}
	}

	
	/**
	 * Same as getPredefinedData(String prefix) but with an empty
	 * prefix.
	 * 
	 * @return predefined data map
	 */
	public static HashMap<String,String> getPredefinedData() {
		return Template.getPredefinedData("");
	}
	
	/**
	 * Returns the predefined data map with the given
	 * string as a prefix to the defined options.
	 * 
	 * @param prefix
	 * 			prefix for the defined options
	 * @return	predefined data map
	 */
	public static HashMap<String,String> getPredefinedData(String prefix) {
		HashMap<String,String> predefinedData = new HashMap<String,String>();

		// init the default data mappings here
		predefinedData.put(prefix + "V", "aeiou"); // vocals
		predefinedData.put(prefix + "C", "bcdfghjklmnpqrstvwxyz"); //consonants
		predefinedData.put(prefix + "N", "0123456789"); // numbers
		predefinedData.put(prefix + "L", "abcdefghijklmnopqrstuvwxyz"); // letters or lower case characters
		
		return predefinedData;
	}

	
	
	/**
	 * Processes all element objects to assemble an possible output
	 * string and updates the global alternatives-flag by asking all element
	 * objects for alternatives after processing.<br />
	 * If needed the element array is filled by parsing the template string
	 * and the stored data is fed to the element objects that request external
	 * data. <i>This happens only if the template string or the stored data array
	 * is changed and when starting for the first time</i>
	 *  
	 * @return a possible id string, matching the given template
	 */
	public String buildString() {	
		String result = "";
		logger.debug(Messages.getString("IdGenerator.NEW_LINE"));
		logger.debug(Messages.getString("Template.ATTEMPT_GENRATE"));
		
		// check if there are any alternatives left
		if (!this.hasAlternatives()) {
			logger.warn(Messages.getString("Template.NO_ALTERNATIVES_LEFT"));
			return "";
		}
		
		// get list of elements from the parser (if update is needed)
		if (this.updateElements) {
			this.elements = Parser.getElements(this.getTemplate());
			this.updateElements = false;
		}

		// assume the worst :)
		this.hasAlternatives = false;
		
		// build the result string
		for(Iterator<IElement> iter = this.elements.iterator();iter.hasNext();) {
			IElement currentElement = iter.next();
			
			// fill with data (if update is needed)
			if (this.updateData && currentElement.needsExternalData()) {
				currentElement.setData(this.data.get(this.prefix + currentElement.getKey()));
			}
	
			
			// this will skip marked resolver elements
			if (currentElement.isResolver()) {
				if (this.nextResolver == null) {
					// set this as the next resolver element if 
					// we don't already have one
					this.nextResolver = currentElement;
				}
				continue;
			}
			
			//logger.debug(this.data.get(this.prefix + currentElement.getKey()));

			// append output to the result string
			if (currentElement.isComplete()) {
				result += currentElement.toString().toLowerCase();
			}
			else {
				logger.fatal(Messages.getString("Template.INCOMPLETE_ELEMENT") + currentElement.getClass().getSimpleName() + " (element=\"" + currentElement.getElement() + "\")");
				System.exit(175);
			}
			
			// update the alternative indicator if there are any left
			if (currentElement.hasAlternatives())
				this.hasAlternatives = true;
			else
				logger.debug(Messages.getString("Element.NO_ALTERNATIVES_LEFT") + currentElement.getClass().getSimpleName() + " (element=" + currentElement.getElement() + ")");
		}

		// either the data was updated or it already was up to date at this point
		this.updateData = false;

		
		if (!this.hasAlternatives && this.nextResolver != null) {
			// this makes the resolver a normal element
			// which is processed exactly like all other elements
			this.nextResolver.setResolver(false);
			this.hasAlternatives = true;

			// this makes the build loop automatically set the next
			// resolver element if one is found
			this.nextResolver = null;
		}
		
		return result;
	}
	
	/**
	 * Update the stored data array with the one given
	 * by merging its entries
	 * 
	 * @param newData
	 * 			new data array to update the existing one with
	 */
	public void updateData(HashMap<String,String> newData) {
		this.data.putAll(newData);
		this.updateData = true;
	}
	
	
	
	
	
	
	// *******************
	// * getter / setter *
	// *******************
	
	/**
	 * Sets the template string and
	 * invokes the parser to translate the template string
	 * to a list of matching element objects
	 * 
	 * @param templateString
	 * 			the new template string
	 */
	public void setTemplate(String templateString) {
		this.template = templateString;
		logger.info(Messages.getString("Template.GOT_TEMPLATE_STRING") + templateString);
		this.updateElements = true;
	}
	
	/**
	 * returns the template string
	 * 
	 * @return the current template string
	 */
	public String getTemplate() {
		if (this.template != null)
			return this.template;
		else
			logger.fatal(Messages.getString("Template.TEMPLATE_STRING_NOT_INITIALIZED"));
			System.exit(176);
			return null;
	}
	
	/**
	 * Returns if this template has alternative results
	 * left to return.
	 * 
	 * @return true if alternate results are available, false otherwise
	 */
	public boolean hasAlternatives() {
		return this.hasAlternatives;
	}
}
