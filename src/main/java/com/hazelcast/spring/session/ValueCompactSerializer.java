package com.hazelcast.spring.session;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class ValueCompactSerializer implements CompactSerializer<AttributeValue> {
    @Override
    public AttributeValue read(CompactReader reader) {
        String value = reader.readString("value");
        if (value == null) {
            return null;
        }
        byte form = reader.readInt8("form");
        AttributeValue.ValueForm formAsEnum = AttributeValue.ValueForm.from(form);
        Object finalValue = switch (formAsEnum) {
            case STRING -> value;
            case DATA -> value.getBytes();
            case LONG ->  Long.parseLong(value);
            case INTEGER ->  Integer.parseInt(value);
        };
        return new AttributeValue(finalValue, formAsEnum);
    }

    @Override
    public void write(CompactWriter writer, AttributeValue object) {
        String finalValue = switch (object.form()) {
            case STRING -> (String) object.object();
            case DATA -> {
                byte[] bytes = (byte[]) object.object();
                yield new String(bytes);
            }
            case LONG, INTEGER ->  object.object().toString();
        };
        writer.writeString("value",  finalValue);
        writer.writeInt8("form", (byte) object.form().ordinal());
    }

    @Override
    public String getTypeName() {
        return "AttributeValue";
    }

    @Override
    public Class<AttributeValue> getCompactClass() {
        return AttributeValue.class;
    }
}
