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

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.calmera.TestUtils;
import com.github.calmera.serde.TestModel;

import static org.assertj.core.api.Assertions.assertThat;

class AvroUtilsTest {

	private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

	@Test
	void testGenerateSchema() throws ValueMappingException {
		Schema actualSchema = AvroUtils.schemaFromClass(TestModel.class);

		logger.info("expected: " + TestModel.SCHEMA);
		logger.info("actual: " + actualSchema);

		assertThat(actualSchema.getFullName()).isEqualTo(TestModel.SCHEMA.getFullName());
		assertThat(actualSchema.getType()).isEqualTo(TestModel.SCHEMA.getType());
		for (Schema.Field field : actualSchema.getFields()) {
			assertThat(field.name()).isEqualTo(TestModel.SCHEMA.getField(field.name()).name());
			assertThat(field.schema()).isEqualTo(TestModel.SCHEMA.getField(field.name()).schema());

			if (TestModel.SCHEMA.getField(field.name()).defaultVal() != null) {
				assertThat(field.defaultVal())
					.withFailMessage("Default value for field " + field.name() + " does not match: expected " + TestModel.SCHEMA.getField(field.name()).defaultVal() + ", actual " + field.defaultVal() + ".")
					.isEqualTo(TestModel.SCHEMA.getField(field.name()).defaultVal());
			}
			else {
				assertThat(field.defaultVal()).isNull();
			}
		}
	}

}
