/*
 * Copyright 2021-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.calmera.dyre;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.calmera.dyre.annotations.DyreField;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.calmera.dyre.DyreUtils.getFieldName;

/**
 * @author Daan Gerits
 * @author Tim Ysewyn
 */
public abstract class AvroUtils {

	private static final Logger logger = LoggerFactory.getLogger(AvroUtils.class);

	public static Schema schemaFromClass(Class<? extends DynamicRecord> recordClass) throws ValueMappingException {
		Schema schema = Schema.createRecord(recordClass.getSimpleName(), null, null, false);

		// -- detect the methods
		Map<String, Method> getters = new HashMap<>();
		Map<String, Method> setters = new HashMap<>();
		Map<String, Method> adders = new HashMap<>();
		Map<String, Method> removers = new HashMap<>();
		Map<String, Map<Class<? extends Annotation>, Annotation>> annotations = new HashMap<>();

		for (Method method : recordClass.getDeclaredMethods()) {
			if (method.getName().startsWith("get")) {
				String fieldName = DyreUtils.getFieldName(method, "get");
				getters.put(fieldName, method);
				addAnnotations(annotations, fieldName, method.getDeclaredAnnotations());
			}
			else if (method.getName().startsWith("is")) {
				String fieldName = DyreUtils.getFieldName(method, "is");
				getters.put(fieldName, method);
				addAnnotations(annotations, fieldName, method.getDeclaredAnnotations());
			}
			else if (method.getName().startsWith("set")) {
				String fieldName = DyreUtils.getFieldName(method, "set");
				setters.put(fieldName, method);
				addAnnotations(annotations, fieldName, method.getDeclaredAnnotations());
			}
			else if (method.getName().startsWith("addTo")) {
				String fieldName = DyreUtils.getFieldName(method, "addTo");
				adders.put(fieldName, method);
				addAnnotations(annotations, fieldName, method.getParameterAnnotations()[0]);
				addAnnotations(annotations, fieldName, method.getDeclaredAnnotations());
			}
			else if (method.getName().startsWith("putInto")) {
				String fieldName = DyreUtils.getFieldName(method, "putInto");
				adders.put(fieldName, method);
				addAnnotations(annotations, fieldName, method.getParameterAnnotations()[0]);
				addAnnotations(annotations, fieldName, method.getDeclaredAnnotations());
			}
			else if (method.getName().startsWith("removeFrom")) {
				String fieldName = DyreUtils.getFieldName(method, "removeFrom");
				removers.put(fieldName, method);
				addAnnotations(annotations, fieldName, method.getDeclaredAnnotations());
			}
		}

		// -- determine the field names
		Set<String> fieldNames = new HashSet<>();
		fieldNames.addAll(getters.keySet());
		fieldNames.addAll(setters.keySet());
		fieldNames.addAll(adders.keySet());
		fieldNames.addAll(removers.keySet());

		// -- determine field annotations
		List<Schema.Field> fields = new ArrayList<>();
		for (String fieldName : fieldNames) {
			try {
				Type fieldType = getTypeOfField(fieldName, getters.get(fieldName), setters.get(fieldName));
				Schema fieldSchema = schemaForField(fieldType, annotations.get(fieldName));
				fields.add(new Schema.Field(fieldName, fieldSchema));
			}
			catch (ValueMappingException vme) {
				throw new ValueMappingException("failed to map field " + fieldName + ": " + vme.getMessage(), vme);
			}
		}
		schema.setFields(fields);

		return schema;
	}

	private static void addAnnotations(Map<String, Map<Class<? extends Annotation>, Annotation>> annotations,
			String fieldName, Annotation[] toAdd) {
		annotations.computeIfAbsent(fieldName, (k) -> new HashMap<>());

		for (Annotation annotation : toAdd) {
			if (annotations.get(fieldName).containsKey(annotation.annotationType())) {
				logger.debug("duplicate annotation " + annotation + " detected for field " + fieldName);
				continue;
			}

			annotations.get(fieldName).put(annotation.annotationType(), annotation);
		}
	}

	private static Schema schemaForField(Type type, Map<Class<? extends Annotation>, Annotation> annotations)
			throws ValueMappingException {

		DyreField anno = (DyreField) annotations.get(DyreField.class);

		if (type instanceof Class<?>) {
			Class<?> clazz = (Class<?>) type;
			if (DynamicRecord.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(schemaFromClass((Class<? extends DynamicRecord>) type), anno);
			}
			else if (String.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().stringType(), anno);
			}
			else if (Boolean.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().booleanType(), anno);
			}
			else if (Integer.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().intType(), anno);
			}
			else if (Long.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().longType(), anno);
			}
			else if (Float.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().floatType(), anno);
			}
			else if (Double.class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().doubleType(), anno);
			}
			else if (byte[].class.isAssignableFrom(clazz)) {
				return makeOptionalIfNeeded(SchemaBuilder.builder().bytesType(), anno);
			}
			else if (clazz.isEnum()) {
				List<String> symbols = new ArrayList<>();
				for (Enum<?> ec : ((Class<? extends Enum<?>>) type).getEnumConstants()) {
					symbols.add(ec.name());
				}

				Schema enumSchema = SchemaBuilder.enumeration(clazz.getSimpleName())
						.symbols(symbols.toArray(new String[] {}));
				return makeOptionalIfNeeded(enumSchema, anno);
			}
		}
		else if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			Class<?> rawReturnType = (Class<?>) parameterizedType.getRawType();

			if (List.class.isAssignableFrom(rawReturnType)) {
				return makeOptionalIfNeeded(SchemaBuilder.array()
						.items(schemaForField(parameterizedType.getActualTypeArguments()[0], annotations)), anno);
			}
			else if (Map.class.isAssignableFrom(rawReturnType)) {
				return makeOptionalIfNeeded(SchemaBuilder.map()
						.values(schemaForField(parameterizedType.getActualTypeArguments()[1], annotations)), anno);
			}
		}
		throw new ValueMappingException(type.getTypeName() + ": unsupported type");
	}

	private static Type getTypeOfField(String fieldName, Method getter, Method setter) throws ValueMappingException {
		if (getter != null) {
			return getter.getGenericReturnType();
		}

		if (setter != null && setter.getParameterTypes().length == 1) {
			return setter.getGenericParameterTypes()[0];
		}

		throw new ValueMappingException("No getter or setter found for field " + fieldName);
	}

	private static Schema makeOptionalIfNeeded(Schema schema, DyreField anno) {
		if (anno != null && !anno.required()) {
			return SchemaBuilder.unionOf().nullType().and().type(schema).endUnion();
		}
		else {
			return schema;
		}
	}

}
