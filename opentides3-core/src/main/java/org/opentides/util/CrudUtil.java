/*
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.    
 */

package org.opentides.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.hibernate.LazyInitializationException;
import org.opentides.annotation.Auditable;
import org.opentides.bean.AuditableField;
import org.opentides.bean.BaseEntity;
import org.opentides.bean.MessageResponse;
import org.opentides.bean.SystemCodes;
import org.opentides.exception.InvalidImplementationException;
import org.springframework.context.MessageSource;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * @author allanctan
 *
 */
public class CrudUtil {

	private static final Logger _log = Logger.getLogger(CrudUtil.class);

	private static final String SQL_PARAM = ":([^\\s]+)";
	private static final Pattern SQL_PARAM_PATTERN = Pattern.compile(SQL_PARAM,
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

	private static final String URL_ENCODING = "UTF-8";

	/**
	 * Hide the constructor.
	 */
	private CrudUtil() {
	}

	/**
	 * Creates the logging message for new audit logs
	 * 
	 * @param obj
	 * @return
	 */
	public static String buildCreateMessage(final BaseEntity obj) {
		final StringBuilder message = new StringBuilder();
		if (obj.getClass().isAnnotationPresent(Auditable.class)) {
			final AuditableField pf = CacheUtil.getPrimaryField(obj);
			message.append("<p class='add-message'>Added new ");
			message.append(buildPrimaryField(pf, obj)).append(
					" with the following: ");

			// loop through the fields list
			final List<AuditableField> auditFields = CacheUtil
					.getAuditable(obj);
			int count = 0;
			for (final AuditableField property : auditFields) {
				Object ret = retrieveNullableObjectValue(obj,
						property.getFieldName());
				ret = normalizeValue(ret);
				if (ret.toString().trim().length() > 0
						&& !pf.getFieldName().equals(property.getFieldName())) {
					if (count > 0) {
						message.append("and ");
					}
					message.append(property.getTitle())
							.append("=<span class='field-value'>")
							.append(ret.toString()).append("</span> ");
					count++;
				}
			}
			message.append("</p>");
		}
		return message.toString();
	}

	/**
	 * Returns the list of fields that have difference values or updated.
	 * 
	 * @param oldObject
	 * @param newObject
	 * @return
	 */
	public static List<String> getUpdatedFields(final BaseEntity oldObject,
			final BaseEntity newObject) {
		final List<String> fields = CacheUtil.getSynchronizableFields(oldObject);
		final List<String> updatedFields = new ArrayList<String>();
		for (final String field : fields) {
			Object oldValue = retrieveNullableObjectValue(oldObject, field);
			Object newValue = retrieveNullableObjectValue(newObject, field);
			oldValue = normalizeValue(oldValue);
			newValue = normalizeValue(newValue);
			if (!oldValue.equals(newValue)) {
				updatedFields.add(field);
			}
		}
		return updatedFields;
	}

	/**
	 * Creates the logging message for update audit logs
	 * 
	 * @param obj
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String buildUpdateMessage(final BaseEntity oldObject,
			final BaseEntity newObject) {

		final StringBuilder message = new StringBuilder(
				"<p class='change-message'>Changed ");
		final AuditableField pf = CacheUtil.getPrimaryField(oldObject);
		message.append(buildPrimaryField(pf, oldObject)).append(
				" with the following: ");
		// loop through the fields list
		final List<AuditableField> auditableFields = CacheUtil
				.getAuditable(oldObject);
		int count = 0;

		// scenarios
		// 1 - collection vs null -> Enter collection
		// 2 - collection vs collection -> Enter collection
		// 3 - object vs null -> Enter object
		// 4 - object vs object -> Enter object
		// 5 - collection vs object -> Invalid or convert collection to object.

		for (final AuditableField property : auditableFields) {
			_log.debug("Building update message for field "
					+ property.getFieldName());
			Object oldValue = retrieveNullableObjectValue(oldObject,
					property.getFieldName());
			Object newValue = retrieveNullableObjectValue(newObject,
					property.getFieldName());
			oldValue = normalizeValue(oldValue);
			newValue = normalizeValue(newValue);

			if (!oldValue.getClass().getCanonicalName().startsWith(
					newValue.getClass().getCanonicalName()) ) {
				_log.debug("Old object: " + oldValue);
				_log.warn("Unable to compare [" + property.getFieldName()
						+ "] for audit logging due to difference in datatype. "
						+ "oldValue is [" + oldValue.getClass()
						+ "] and newValue is [" + newValue.getClass() + "]");
				continue;
			}

			if (Collection.class.isAssignableFrom(oldValue.getClass())
					&& Collection.class.isAssignableFrom(newValue.getClass())) {
				if (((Collection) oldValue).isEmpty()
						&& ((Collection) newValue).isEmpty()) {
					_log.debug("Old and New values are empty");
					continue;
				}
				_log.debug("Old and New values are not empty");
				final List addedList = new ArrayList((Collection) newValue);
				// Collection addedCollection = (Collection)newValue;
				// addedCollection.removeAll((Collection)oldValue);
				// addedList.addAll(new ArrayList((Collection)newValue));
				addedList.removeAll(new ArrayList((Collection) oldValue));

				final List removedList = new ArrayList((Collection) oldValue);
				// removedList.addAll((List) oldValue);
				removedList.removeAll(new ArrayList((Collection) newValue));
				// Collection removedCollection = (Collection)oldValue;
				// removedCollection.remove((Collection)newValue);
				if (!addedList.isEmpty() || !removedList.isEmpty()) {
					if (count > 0) {
						message.append("and ");
					}
				}
				if (!addedList.isEmpty()) {
					message.append("added ").append(property.getTitle())
							.append(" <span class='field-values-added'>")
							.append(addedList).append("</span> ");
				}
				if (!removedList.isEmpty()) {
					if (!addedList.isEmpty()) {
						message.append("and ");
					}
					message.append("removed ").append(property.getTitle())
							.append(" <span class='field-values-removed'>")
							.append(removedList).append("</span> ");
					count++;
				}
			} else {
				if (!oldValue.equals(newValue)) {
					if (count > 0) {
						message.append("and ");
					}
					if (StringUtil.isEmpty(newValue.toString())) {
						message.append(property.getTitle())
								.append(" <span class='field-value-removed'>")
								.append(oldValue.toString())
								.append("</span> is removed ");
					} else {
						message.append(property.getTitle());
						if (!StringUtil.isEmpty(oldValue.toString())) {
							message.append(
									" from <span class='field-value-from'>")
									.append(oldValue.toString())
									.append("</span> ");
						} else {
							message.append(" is set ");
						}
						message.append("to <span class='field-value-to'>")
								.append(newValue.toString()).append("</span> ");
					}
					count++;
				}
			}
		}
		message.append("</p>");
		if (count == 0) {
			return "";
		} else {
			return message.toString();
		}
	}

	/**
	 * Creates the logging message for deleted records.
	 * 
	 * @param obj
	 * @return
	 */
	public static String buildDeleteMessage(final BaseEntity obj) {
		final StringBuilder message = new StringBuilder(
				"<p class='delete-message'>Deleted ");
		final AuditableField pf = CacheUtil.getPrimaryField(obj);
		message.append(buildPrimaryField(pf, obj)).append("</p>");
		return message.toString();
	}

	/**
	 * Private helper to build primary field message.
	 * 
	 * @param pf
	 * @param obj
	 * @return
	 */
	private static String buildPrimaryField(final AuditableField pf,
			final BaseEntity obj) {
		final StringBuilder message = new StringBuilder();
		final String shortName = CacheUtil.getReadableName(obj);
		message.append(shortName); // class name
		final Object value = retrieveNullableObjectValue(obj, pf.getFieldName());
		if (value != null && !StringUtil.isEmpty(value.toString())) {
			message.append(" with ").append(pf.getTitle())
					.append(":<span class='primary-field'>")
					.append(value.toString()).append("</span>");
		}
		return message.toString();
	}

	/**
	 * Private helper that converts object into other form for audit log
	 * comparison and display purposes.
	 * 
	 * @param obj
	 * @return
	 */
	private static Object normalizeValue(final Object obj) {
		// convert date into string
		if (obj == null) {
			return "";
		}
		// if object is empty collection, convert to empty string?
		if (obj instanceof Collection) {
			try {
				if (((Collection<?>) obj).isEmpty()) {
					return "";
				}
			} catch (final LazyInitializationException lie) {
				// TODO: Need to check if this is safe
				return "";
			}
		}

		if (obj instanceof Date) {
			if (DateUtil.hasTime((Date) obj)) {
				return DateUtil.dateToString((Date) obj,
						"EEE, dd MMM yyyy HH:mm:ss z");
			} else {
				return DateUtil.dateToString((Date) obj, "EEE, dd MMM yyyy");
			}
		}
		return obj;
	}

	/**
	 * Helper function that converts the given object into a map value.
	 * 
	 * @param object
	 * @return
	 */
	public static Map<String, Object> buildMapValues(final BaseEntity object) {
		return buildMapValues(object, CacheUtil.getPersistentFields(object));
	}

	/**
	 * Helper function that converts the given object into a map value.
	 * 
	 * @param object
	 * @param fields
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static Map<String, Object> buildMapValues(final BaseEntity object,
			final List<String> fields) {
		final Map<String, Object> map = new HashMap<String, Object>();
		for (final String property : fields) {
			// get the value
			final Object ret = retrieveObjectValue(object, property);
			if (ret != null) {
				if (SystemCodes.class.isAssignableFrom(ret.getClass())) {
					final SystemCodes sc = (SystemCodes) ret;
					if (!StringUtil.isEmpty(sc.getKey())) {
						map.put(property, sc.getKey());
					}
				} else if (BaseEntity.class.isAssignableFrom(ret.getClass())) {
					final BaseEntity be = (BaseEntity) ret;
					if (be.getId() != null) {
						map.put(property, be.getId());
					}
				} else if (Collection.class.isAssignableFrom(ret.getClass())) {
					final Collection c = (Collection) ret;
					final Iterator itr = c.iterator();
					while (itr.hasNext()) {
						final Object value = itr.next();
						if (value != null) {
							if (BaseEntity.class.isAssignableFrom(value
									.getClass())) {
								final BaseEntity be = (BaseEntity) value;
								if (be.getId() != null) {
									map.put(property, be.getId());
								}
							} else {
								map.put(property, value);
							}
						}
					}
				} else if (ret.toString().trim().length() > 0) {
					map.put(property, ret.toString());
				}
			}
		}
		return map;
	}

	/**
	 * Builds the URL parameter for invoking search services via REST
	 * 
	 * @param example
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static String buildURLParameters(final BaseEntity object) {
		final Map<String, Object> map = buildMapValues(object);
		final StringBuilder parameter = new StringBuilder("");
		int count = 0;
		try {
			for (final String key : map.keySet()) {
				if (count > 0) {
					parameter.append("&");
				}
				parameter
						.append(key)
						.append("=")
						.append(URLEncoder.encode(map.get(key).toString(),
								URL_ENCODING));
				count++;
			}
		} catch (final UnsupportedEncodingException ex) {
			_log.error(URL_ENCODING + " is not supported", ex);
		}
		if (count > 0) {
			return parameter.toString();
		} else {
			return "";
		}
	}

	/**
	 * Builds the query string appended to queryByExample
	 * 
	 * @param example
	 * @param exactMatch
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static String buildJpaQueryString(final BaseEntity example,
			final boolean exactMatch) {
		int count = 0;
		final StringBuilder clause = new StringBuilder(" where ");
		final List<String> exampleFields = CacheUtil
				.getSearchableFields(example);
		for (String property : exampleFields) {
			// get the value
			final Object ret = retrieveObjectValue(example, property);
			// append the alias
			property = "obj." + property;
			if (ret != null) {
				if (String.class.isAssignableFrom(ret.getClass())
						&& !exactMatch) {
					if (ret.toString().trim().length() > 0) {
						if (count > 0) {
							clause.append(" and ");
						}
						clause.append(property)
								.append(" like '%")
								.append(StringUtil.escapeSql(ret.toString(),
										true)).append("%'")
								.append(" escape '\\'"); // escape for Oracle
															// database
						count++;
					}
				} else if (SystemCodes.class.isAssignableFrom(ret.getClass())) {
					final SystemCodes sc = (SystemCodes) ret;
					if (!StringUtil.isEmpty(sc.getKey())) {
						if (count > 0) {
							clause.append(" and ");
						}
						clause.append(property).append(".key").append(" = '")
								.append(sc.getKey() + "'");
						count++;
					}
				} else if (BaseEntity.class.isAssignableFrom(ret.getClass())) {
					final BaseEntity be = (BaseEntity) ret;
					if (be.getId() != null) {
						if (count > 0) {
							clause.append(" and ");
						}
						clause.append(property).append(".id").append(" = ")
								.append(be.getId());
						count++;
					}
				} else if (Integer.class.isAssignableFrom(ret.getClass())
						|| Float.class.isAssignableFrom(ret.getClass())
						|| Long.class.isAssignableFrom(ret.getClass())
						|| Double.class.isAssignableFrom(ret.getClass())
						|| BigDecimal.class.isAssignableFrom(ret.getClass())
						|| Boolean.class.isAssignableFrom(ret.getClass())) {
					// numeric types doesn't need to be enclosed in single
					// quotes
					if (ret.toString().trim().length() > 0) {
						if (count > 0) {
							clause.append(" and ");
						}
						clause.append(property).append(" = ")
								.append(ret.toString());
						count++;
					}
				} else if (Class.class.isAssignableFrom(ret.getClass())) {
					if (count > 0) {
						clause.append(" and ");
					}
					final Class clazz = (Class) ret;
					clause.append(property).append(" = '")
							.append(clazz.getName()).append("'");
					count++;
				} else if (Collection.class.isAssignableFrom(ret.getClass())) {
					// not supported yet
					_log.warn("FindByExample on type Collection is not supported.");
				} else if (ret.toString().trim().length() > 0) {
					if (count > 0) {
						clause.append(" and ");
					}
					clause.append(property)
							.append(" = '")
							.append(StringUtil.escapeSql(ret.toString(), false))
							.append("'");
					count++;
				}
			}
		}
		if (count > 0) {
			return clause.toString();
		} else {
			return "";
		}
	}

	/**
	 * This method retrieves the object value that corresponds to the property
	 * specified. This method can recurse inner classes until specified property
	 * is reached.
	 * 
	 * For example: obj.firstName obj.address.Zipcode
	 * 
	 * @param obj
	 * @param property
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static Object retrieveObjectValue(final Object obj,
			final String property) {
		if (property.contains(".")) {
			// we need to recurse down to final object
			final String props[] = property.split("\\.");
			try {
				Object ivalue = null;
				if (Map.class.isAssignableFrom(obj.getClass())) {
					final Map map = (Map) obj;
					ivalue = map.get(props[0]);
				} else {
					Method method;
					if (props[0].startsWith("get")) {
						method = obj.getClass().getMethod(props[0]);
					} else {
						method = obj.getClass().getMethod(
								NamingUtil.toGetterName(props[0]));
					}
					ivalue = method.invoke(obj);
				}
				if (ivalue == null) {
					return null;
				}
				// traverse collection objects
				if (Collection.class.isAssignableFrom(ivalue.getClass())) {
					final Iterator iter = ((Collection) ivalue).iterator();
					final List<Object> ret = new ArrayList<Object>();
					final String prop = property
							.substring(props[0].length() + 1);
					while (iter.hasNext()) {
						final Object lvalue = iter.next();
						if (lvalue != null) {
							ret.add(retrieveObjectValue(lvalue, prop));
						}
					}
					return ret;
				}
				return retrieveObjectValue(ivalue,
						property.substring(props[0].length() + 1));
			} catch (final Exception e) {
				throw new InvalidImplementationException(
						"Failed to retrieve value for " + property, e);
			}
		} else {
			// let's get the object value directly
			try {
				if (Map.class.isAssignableFrom(obj.getClass())) {
					final Map map = (Map) obj;
					return map.get(property);
				} else {
					Method method;
					if (property.startsWith("get")) {
						method = obj.getClass().getMethod(property);
					} else {
						method = obj.getClass().getMethod(
								NamingUtil.toGetterName(property));
					}
					return method.invoke(obj);
				}
			} catch (final Exception e) {
				throw new InvalidImplementationException(
						"Failed to retrieve value for " + property, e);
			}
		}
	}

	/**
	 * 
	 * This method retrieves the object value that corresponds to the property
	 * specified. This method supports nullable fields.
	 * 
	 * @see retrieveObjectValue
	 * @param obj
	 * @param property
	 * @return
	 */
	public static Object retrieveNullableObjectValue(final Object obj,
			final String property) {
		try {
			return retrieveObjectValue(obj, property);
		} catch (final Exception e) {
			return null;
		}
	}

	/**
	 * This method retrieves the object type that corresponds to the property
	 * specified. This method can recurse inner classes until specified property
	 * is reached.
	 * 
	 * For example: obj.firstName obj.address.Zipcode
	 * 
	 * @param obj
	 * @param property
	 * @return
	 */
	public static Class<?> retrieveObjectType(final Object obj,
			final String property) {
		if (property.contains(".")) {
			// we need to recurse down to final object
			final String props[] = property.split("\\.");
			try {
				final Method method = obj.getClass().getMethod(
						NamingUtil.toGetterName(props[0]));
				final Object ivalue = method.invoke(obj);
				return retrieveObjectType(ivalue,
						property.substring(props[0].length() + 1));
			} catch (final Exception e) {
				throw new InvalidImplementationException(
						"Failed to retrieve value for " + property, e);
			}
		} else {
			// let's get the object value directly
			try {
				final Method method = obj.getClass().getMethod(
						NamingUtil.toGetterName(property));
				return method.getReturnType();
			} catch (final Exception e) {
				throw new InvalidImplementationException(
						"Failed to retrieve value for " + property, e);
			}
		}
	}

	/**
	 * This method evaluates the given expression from the object. This method
	 * now uses Spring Expression Language (SpEL).
	 * 
	 * @param obj
	 * @param expression
	 * @return
	 */
	public static Boolean evaluateExpression(final Object obj,
			final String expression) {
		if (StringUtil.isEmpty(expression)) {
			return false;
		}
		try {
			final ExpressionParser parser = new SpelExpressionParser();
			final Expression exp = parser.parseExpression(expression);
			return exp.getValue(obj, Boolean.class);
		} catch (final Exception e) {
			_log.debug("Failed to evaluate expression [" + expression
					+ "] for object [" + obj.getClass() + "].");
			_log.debug(e.getMessage());
			return false;
		}
	}

	/**
	 * This method will replace SQL parameters with respective values from the
	 * object.
	 * 
	 * @param sql
	 * @param obj
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static String replaceSQLParameters(String sql, final Object obj) {
		// let's get all sql parameter by expression
		final Matcher sqlMatcher = CrudUtil.SQL_PARAM_PATTERN.matcher(sql);
		while (sqlMatcher.find()) {
			final String param = sqlMatcher.group(1);
			final Object valueObject = CrudUtil.retrieveNullableObjectValue(
					obj, param);
			if (valueObject == null) {
				sql = sql.replace(sqlMatcher.group(), "null");
			} else if (String.class.isAssignableFrom(valueObject.getClass())) {
				sql = sql.replace(sqlMatcher.group(),
						"'" + valueObject.toString() + "'");
			} else if (Collection.class
					.isAssignableFrom(valueObject.getClass())) {
				final Collection<Object> list = (Collection<Object>) valueObject;
				int ctr = 0;
				final StringBuilder buff = new StringBuilder();
				for (final Object item : list) {
					if (ctr++ > 0) {
						buff.append(", ");
					}
					if (SystemCodes.class.isAssignableFrom(item.getClass())) {
						final SystemCodes entity = (SystemCodes) item;
						// use id
						buff.append("'").append(entity.getKey()).append("'");
					} else if (BaseEntity.class.isAssignableFrom(item
							.getClass())) {
						final BaseEntity entity = (BaseEntity) item;
						// use id
						buff.append(entity.getId());
					} else {
						buff.append("'").append(item.toString()).append("'");
					}
				}
				sql = sql.replace(sqlMatcher.group(), buff.toString());
			} else {
				sql = sql.replace(sqlMatcher.group(), valueObject.toString());
			}
		}
		return sql;
	}

	/**
	 * Overloaded method to retrieve all fields, including fields from parent
	 * class.
	 * 
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings({ "rawtypes" })
	public static List<Field> getAllFields(final Class clazz) {
		return CrudUtil.getAllFields(clazz, true);
	}

	/**
	 * Helper method to retrieve all fields of a class including fields declared
	 * in its superclass.
	 * 
	 * @param clazz
	 * @param includeParent
	 * @return
	 */
	@SuppressWarnings({ "rawtypes" })
	public static List<Field> getAllFields(final Class clazz,
			final boolean includeParent) {
		final List<Field> fields = new ArrayList<Field>();
		if (BaseEntity.class.isAssignableFrom(clazz) && includeParent) {
			fields.addAll(getAllFields(clazz.getSuperclass(), includeParent));
		}
		for (final Field field : clazz.getDeclaredFields()) {
			fields.add(field);
		}
		return fields;
	}

	/**
	 * Overloaded method to retrieve all methods, including methods from parent
	 * class.
	 * 
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings({ "rawtypes" })
	public static List<Method> getAllMethods(final Class clazz) {
		return CrudUtil.getAllMethods(clazz, true);
	}

	/**
	 * Helper method to retrieve all methods of a class including methods
	 * declared in its superclass.
	 * 
	 * @param clazz
	 * @param includeParent
	 * @return
	 */
	@SuppressWarnings({ "rawtypes" })
	public static List<Method> getAllMethods(final Class clazz,
			final boolean includeParent) {
		final List<Method> methods = new ArrayList<Method>();
		if (BaseEntity.class.isAssignableFrom(clazz) && includeParent) {
			methods.addAll(getAllMethods(clazz.getSuperclass(), includeParent));
		}
		for (final Method method : clazz.getDeclaredMethods()) {
			methods.add(method);
		}
		return methods;
	}

	/**
	 * Converts the binding error messages to list of MessageResponse
	 * 
	 * @param bindingResult
	 */
	public static List<MessageResponse> convertErrorMessage(
			final BindingResult bindingResult, final Locale locale,
			final MessageSource messageSource) {
		final List<MessageResponse> errorMessages = new ArrayList<MessageResponse>();
		if (bindingResult.hasErrors()) {
			for (final ObjectError error : bindingResult.getAllErrors()) {
				MessageResponse message = null;
				if (error instanceof FieldError) {
					final FieldError ferror = (FieldError) error;
					message = new MessageResponse(MessageResponse.Type.error,
							error.getObjectName(), ferror.getField(),
							error.getCodes(), error.getArguments());
				} else {
					message = new MessageResponse(MessageResponse.Type.error,
							error.getObjectName(), null, error.getCodes(),
							error.getArguments());
				}
				message.setMessage(messageSource.getMessage(message, locale));
				errorMessages.add(message);
			}
		}
		return errorMessages;
	}

	/**
	 * Builds success message by convention. Success messages are displayed as
	 * notifications only.
	 * 
	 * Standard convention in order of resolving message is: (1)
	 * message.<className>.
	 * <code>-success (e.g. message.system-codes.add-success)
	 *     (2) message.add-success (generic message)
	 * 
	 * @param elementClass
	 * @param object
	 * @param code
	 * @param locale
	 * @return
	 */
	public static List<MessageResponse> buildSuccessMessage(
			final BaseEntity object, final String code, final Locale locale,
			final MessageSource messageSource) {
		final List<MessageResponse> messages = new ArrayList<MessageResponse>();
		Assert.notNull(object);
		final String prefix = "message."
				+ NamingUtil.toElementName(object.getClass().getSimpleName());
		final String codes = prefix + "." + code + "-success,message." + code
				+ "-success";
		final MessageResponse message = new MessageResponse(
				MessageResponse.Type.notification, codes.split("\\,"), null);
		message.setMessage(messageSource.getMessage(message, locale));
		messages.add(message);
		return messages;
	}

	/**
	 * Helper method to check if the object is a type of collection or map.
	 * 
	 * @param ob
	 * @return
	 */
	public static boolean isCollection(final Object ob) {
		return ob instanceof Collection || ob instanceof Map;
	}

	/**
	 * Clones a base entity using serialization. Make sure that all the classes
	 * in object's graph are serializable.
	 * 
	 * @param source
	 * @return
	 */
	public static BaseEntity clone(final BaseEntity source) {
		try {
			final ByteArrayOutputStream buf = new ByteArrayOutputStream();
			final ObjectOutputStream o = new ObjectOutputStream(buf);
			o.writeObject(source);

			final ObjectInputStream in = new ObjectInputStream(
					new ByteArrayInputStream(buf.toByteArray()));
			return (BaseEntity) in.readObject();
		} catch (ClassNotFoundException | IOException e) {
			_log.error("Failed to clone source object " + source.getClass(), e);
		}

		return null;
	}

}