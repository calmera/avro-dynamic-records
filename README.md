# Avro Dynamic Records
Avro Dynamic Records (DyRe) introduces a new way of working with data in your event-processing application. Instead of
generating out code, it takes the approach of writing an interface and seamlessly link it to the data underneath.

Avro Dynamic Record currently builds on top of Apache Avro which is a data serialization system. It allows you to
transform your application data into a format that can be stored or transmitted over the wire. Avro relies on a schema
to be defined for the data. This schema is used to serialize and deserialize the data.

Apache Kafka only knows about byte arrays. It doesn't know anything about the data that is being transmitted, nor does
it care. It simply passes the data along. This is where Avro comes in. When building a stream processing application,
you will also need to define so called Serializers and Deserializers. These are classes that know how to convert the
data into a byte array and back again.

There are two different ways of working with Avro. The first is to use the SpecificRecord interface, which allows you
to generate your classes based on a schema. At first, this sounds great, but it soon turns out to be problematic when
the schema changes and you want your applications to be tolerant to these changes.

The second is to use the GenericRecord interface, which allows you to work with the data in a more generic way. It
provides more flexibility, but also more complexity. It is a pain to work with because it simply is too generic. We
actually want to have a strong-typed interface to our data.

Dynamic Proxies allow us to define an interface and define what needs to happen when a method is called. This is
exactly what we want; the strong typed interface translating to and from a GenericRecord. Serializers and Deserializers
are available to hook these dynamic records into your kafka streaming logic.

## Getting Started
### Add the dependency to your maven project
```xml
    <dependency>
        <groupId>io.github.calmera</groupId>
        <artifactId>avro-dynamic-records</artifactId>
        <version>1.0.0</version>
    </dependency>
```

### Put your avro schema in the schema registry
```avro schema
{
  "type": "record",
  "name": "TestModel",
  "namespace": "example",
  "fields": [
    {
      "name": "optionalValue",
      "type": [
        "null",
        "string"
      ],
      "default": null
    },
    {
      "name": "requiredValue",
      "type": "string"
    }
  ]
}
```

### Define your record interface
```java
package example;

import com.github.calmera.dyre.DynamicRecord;
import com.github.calmera.dyre.annotations.DyreField;
import com.github.calmera.dyre.annotations.DyreRecord;

@DyreRecord
public interface TestModel extends DynamicRecord {
    // an optional field
    @DyreField(required = false)
    String getOptionalValue();
    void setOptionalValue(String value);

    // a required field, no need to specify required = true since it is the default
    String getRequiredValue();
    void setRequiredValue(String value);
}
```

### Boot the DynamicRecords class
```java
SchemaRegistryClient sr = ... // a reference to your schema registry
new DynamicRecords();
```

### Create a new record instance
```java
TestModel record = return DynamicRecords.getInstance().newRecord(TestModel.class, Map.of("required_value", "my_value"));

record.getOptionalValue(); // returns null
record.getRequiredValue(); // returns "my_value"
```

## How it works
The `DynamicRecords` class is the entrypoint to the library. It provides methods to create a new record from a
`DynamicRecord` interface. `DynamicRecords` relies on a schema registry to retrieve the schemas referred to by the
records defined further down. There is no need to keep a reference to the DynamicRecords instance, as it is a singleton.

```java
static void init() throws Exception {
    SchemaRegistryClient sr = MockSchemaRegistry.getClientForScope("default");
    TestUtils.registerSchema(sr, TestModel.class, TestModel.SCHEMA);

    new DynamicRecords(sr);
}
```

You will need to write an interface yourself, extending the `DynamicRecord` interface and
annotating it with the `@DyreRecord` annotation. This annotation takes a subject as a parameter or if left empty, it
takes the full class name (package + class name) as the subject.

```java
@DyreRecord
public interface TestModel extends DynamicRecord { }
```

or

```java
@DyreRecord("my.awesome.app.TestModel")
public interface TestModel extends DynamicRecord { }
```

Creating a record instance is done by calling the `newRecord` method on the `DynamicRecords` instance. This method will
retrieve the schema from the schema registry and create a new GenericRecord instance based on the schema and fill in
the initial field values. It will then create a new dynamic proxy with a `GenericRecordInvocationHandler`, passing
the generic record.

The `GenericRecordInvocationHandler` is where the magic happens. It implements the `InvocationHandler` interface and
will react to method calls on the dynamic proxy. It will check if the method is a getter or a setter and will either
get or set the value on the generic record. It will also check if the field is required and will throw an exception
if the field is not set.

## About
I was able to build most of this library as part of my work at KOR Financial. It used to be part of the
[Kopper project](https://github.com/KOR-Financial/kopper), but has been extracted into its own library to make it
easier for others to use. Let me know what can be improved or if you have any questions.
