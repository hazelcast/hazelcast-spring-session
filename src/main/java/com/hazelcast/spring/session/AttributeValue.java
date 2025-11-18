package com.hazelcast.spring.session;

import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;

import java.io.Serial;
import java.io.Serializable;

/**
 * Class used by {@link ExtendedMapSession} to store attribute values with its corresponding type.
 * <p>
 * In client-server architecture we don't want to hold users' Java objects to avoid the need to upload user code to server.
 * However, for speed and simplicity, few types are stored as-is: String, Integer, Long.
 */
public record AttributeValue(Object object, ValueForm form) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static GenericRecord toGenericRecord(Object value) {
        if (value instanceof AttributeValue av) {
            var builder = GenericRecordBuilder.compact("AttributeValue");
            builder.setString("value", (String) av.object());
            builder.setInt8("form", (byte) av.form.ordinal());
            return builder.build();
        }
        return null;
    }

    public static AttributeValue string(Object value) {
        return value == null ? null : new AttributeValue(value, ValueForm.STRING);
    }

    public static AttributeValue data(Object value) {
        return value == null ? null : new AttributeValue(value, ValueForm.DATA);
    }

    public enum ValueForm implements Serializable {
        STRING,
        INTEGER,
        LONG,
        DATA;

        static ValueForm from(byte ord) {
            for (ValueForm value : values()) {
                if (value.ordinal() == ord) {
                    return value;
                }
            }
            throw new IllegalArgumentException();
        }
    }
}
