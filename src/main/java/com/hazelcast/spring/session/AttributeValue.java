package com.hazelcast.spring.session;

import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;

import java.io.Serializable;
import java.util.Objects;

public final class AttributeValue implements Serializable {
    private final Object object;
    private final ValueForm form;

    public AttributeValue(Object object, ValueForm form) {
        this.object = object;
        this.form = form;
    }

    public static GenericRecord toGenericRecord(Object value) {
        if (value instanceof AttributeValue av) {
            var builder = GenericRecordBuilder.compact("AttributeValue");
            builder.setString("value", (String) av.object());
            builder.setInt8("form", (byte) av.form.ordinal());
            return builder.build();
        }
        return null;
    }

    public Object object() {
        return object;
    }

    public ValueForm form() {
        return form;
    }

    public static AttributeValue string(Object value) {
        return value == null ? null : new AttributeValue(value, ValueForm.STRING);
    }

    public static AttributeValue data(Object value) {
        return value == null ? null : new AttributeValue(value, ValueForm.DATA);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (AttributeValue) obj;
        return Objects.equals(this.object, that.object) &&
                Objects.equals(this.form, that.form);
    }

    @Override
    public int hashCode() {
        return Objects.hash(object, form);
    }

    @Override
    public String toString() {
        return "AttributeValue[" +
                "object=" + object + ", " +
                "form=" + form + ']';
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
