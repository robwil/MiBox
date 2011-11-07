package com.robwilliams.mibox.util;

import java.util.List;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;

public class SimpleDBUtil {
	
	/**
	 * Given a SimpleDB select expression which expects a single row result, return either the attribute requested if it exists
	 * or return the given default value. The default value is also returned if the query returns empty, more than one row, or
	 * throws an exception of any kind.
	 * @param sdb AmazonSimpleDB instance to use for the query
	 * @param selectExpression select expression to use
	 * @param attributeName the name of the attribute whose value is to be queried for and returned
	 * @param defaultValue default value to return in case of unexpected or error situations
	 * @return either the requested attribute or the default value
	 */
	public static String selectAttributeFromSingleRow(AmazonSimpleDB sdb, String selectExpression,
													  String attributeName, String defaultValue) {
		String returnValue = null;
		try {
			SelectRequest selectRequest = new SelectRequest(selectExpression);
			List<Item> items = sdb.select(selectRequest).getItems();		 
			// make sure query returned exactly 1 row, as that is the expected case
			if (items != null && items.size() == 1) {
				Item item = items.get(0);
				// find attribute that was requested and return the value, if found
				List<Attribute> attributes = item.getAttributes();
				for (Attribute attribute : attributes) {
					if (attribute.getName().equals(attributeName)) {
						returnValue = attribute.getValue();
					}
				}
			}
			if (returnValue != null) {
				return returnValue;
			}
		} catch (Exception e) {
			return defaultValue;
		}
		
		return defaultValue;
	}
	
	/**
	 * Takes a string that is intended to be used within single quotes and escapes all single quotes.
	 * The SDB rules for escaping is to simply double the quote. So escaping ' is done by replacing
	 * every ' with ''.
	 * @param str
	 * @return
	 */
	public static String escapeSingleQuotedString(String str) {
		return str.replaceAll("'", "''");
	}
}
