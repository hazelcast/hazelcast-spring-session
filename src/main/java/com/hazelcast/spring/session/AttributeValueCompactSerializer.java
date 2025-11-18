package com.hazelcast.spring.session;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import org.jspecify.annotations.NonNull;

public class AttributeValueCompactSerializer implements CompactSerializer<AttributeValue> {
    @Override
    @NonNull
    public AttributeValue read(CompactReader reader) {
        String value = reader.readString("value");
        byte form = reader.readInt8("dataType");
        AttributeValue.AttributeValueDataType formAsEnum = AttributeValue.AttributeValueDataType.from(form);
        if (value == null) {
            return new AttributeValue(null, formAsEnum);
        }
        Object finalValue = switch (formAsEnum) {
            case STRING -> value;
            case DATA -> value.getBytes();
            case LONG ->  Long.parseLong(value);
            case INTEGER ->  Integer.parseInt(value);
        };
        return new AttributeValue(finalValue, formAsEnum);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull AttributeValue object) {
        String finalValue = switch (object.dataType()) {
            case STRING -> (String) object.object();
            case DATA -> {
                byte[] bytes = (byte[]) object.object();
                yield new String(bytes);
            }
            case LONG, INTEGER ->  object.object().toString();
        };
        writer.writeString("value",  finalValue);
        writer.writeInt8("dataType", (byte) object.dataType().ordinal());
    }

    @Override
    @NonNull
    public String getTypeName() {
        return "AttributeValue";
    }

    @Override
    @NonNull
    public Class<AttributeValue> getCompactClass() {
        return AttributeValue.class;
    }
}
