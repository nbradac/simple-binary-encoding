/*
 * Copyright 2014 - 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.sbe.ir;

import uk.co.real_logic.agrona.Verify;

import java.util.function.Supplier;

/**
 * Class to encapsulate a token of information for the message schema stream. This Intermediate Representation (IR)
 * is intended to be language, schema, platform independent.
 *
 * Processing and optimization could be run over a list of Tokens to perform various functions
 * <ul>
 * <li>re-ordering of fields based on encodedLength</li>
 * <li>padding of fields in order to provide expansion room</li>
 * <li>computing offsets of individual fields</li>
 * <li>etc.</li>
 * </ul>
 *
 * IR could be used to generate code or other specifications. It should be possible to do the
 * following:
 * <ul>
 * <li>generate a FIX/SBE schema from IR</li>
 * <li>generate an ASN.1 spec from IR</li>
 * <li>generate a GPB spec from IR</li>
 * <li>etc.</li>
 * </ul>
 *
 * IR could be serialized to storage or network via code generated by SBE. Then read back in to
 * a List of {@link Token}s.
 *
 * The entire IR of an entity is a {@link java.util.List} of {@link Token} objects. The order of this list is
 * very important. Encoding of fields is done by nodes pointing to specific encoding {@link uk.co.real_logic.sbe.PrimitiveType}
 * objects. Each encoding node contains encodedLength, offset, byte order, and {@link Encoding}. Entities relevant
 * to the encoding such as fields, messages, repeating groups, etc. are encapsulated in the list as nodes
 * themselves. Although, they will in most cases never be serialized. The boundaries of these entities
 * are delimited by BEGIN and END {@link Signal} values in the node {@link Encoding}.
 * A list structure like this allows for each concatenation of encodings as well as easy traversal.
 *
 * An example encoding of a message headerStructure might be like this.
 * <ul>
 * <li>Token 0 - Signal = BEGIN_MESSAGE, schemaId = 100</li>
 * <li>Token 1 - Signal = BEGIN_FIELD, schemaId = 25</li>
 * <li>Token 2 - Signal = ENCODING, PrimitiveType = uint32, encodedLength = 4, offset = 0</li>
 * <li>Token 3 - Signal = END_FIELD</li>
 * <li>Token 4 - Signal = END_MESSAGE</li>
 * </ul>
 */
public class Token
{
    /**
     * Indicates how the version field should be interpreted.
     */
    public enum VersionContext
    {
        /**
         * Indicates the version is for the template itself.
         */
        TEMPLATE_VERSION,

        /**
         * Indicates the field was introduced since this template version.
         */
        SINCE_TEMPLATE_VERSION
    }

    /**
     * Invalid ID value.
     */
    public static final int INVALID_ID = -1;

    /**
     * Length not determined
     */
    public static final int VARIABLE_LENGTH = -1;

    /**
     * Offset not computed or set
     */
    public static final int UNKNOWN_OFFSET = -1;

    private final Signal signal;
    private final String name;
    private final int id;
    private final int version;
    private final int encodedLength;
    private final int offset;
    private final Encoding encoding;

    /**
     * Construct an {@link Token} by providing values for all fields.
     *
     * @param signal        for the token role
     * @param name          of the token in the message
     * @param id            as the identifier in the message declaration
     * @param version       application within the template
     * @param encodedLength of the component part
     * @param offset        in the underlying message as octets
     * @param encoding      of the primitive field
     */
    public Token(
        final Signal signal,
        final String name,
        final int id,
        final int version,
        final int encodedLength,
        final int offset,
        final Encoding encoding)
    {
        Verify.notNull(signal, "signal");
        Verify.notNull(name, "name");
        Verify.notNull(encoding, "encoding");

        this.signal = signal;
        this.name = name;
        this.id = id;
        this.version = version;
        this.encodedLength = encodedLength;
        this.offset = offset;
        this.encoding = encoding;
    }

    /**
     * Signal the role of this token.
     *
     * @return the {@link Signal} for the token.
     */
    public Signal signal()
    {
        return signal;
    }

    /**
     * Return the name of the token
     *
     * @return name of the token
     */
    public String name()
    {
        return name;
    }

    /**
     * Return the ID of the token assigned by the specification
     *
     * @return ID of the token assigned by the specification
     */
    public int id()
    {
        return id;
    }

    /**
     * The version context for this token.
     *
     * @return version context for this token.
     * @see Token#versionContext()
     */
    public int version()
    {
        return version;
    }

    /**
     * The context in which the version field should be interpreted.
     *
     * @return context in which the version field should be interpreted.
     */
    public VersionContext versionContext()
    {
        if (signal == Signal.BEGIN_MESSAGE || signal == Signal.END_MESSAGE)
        {
            return VersionContext.TEMPLATE_VERSION;
        }
        else
        {
            return VersionContext.SINCE_TEMPLATE_VERSION;
        }
    }

    /**
     * The encodedLength of this token in bytes.
     *
     * @return the encodedLength of this node. A value of 0 means the node has no encodedLength when encoded. A value of
     * {@link Token#VARIABLE_LENGTH} means this node represents a variable length field.
     */
    public int encodedLength()
    {
        return encodedLength;
    }

    /**
     * The number of encoded primitives in this type.
     *
     * @return number of encoded primitives in this type.
     */
    public int arrayLength()
    {
        if (null == encoding.primitiveType() || 0 == encodedLength)
        {
            return 0;
        }

        return encodedLength / encoding.primitiveType().size();
    }

    public CharSequence switchArray(Supplier<CharSequence> single, Supplier<CharSequence> array)
    {
        final int arrayLength = arrayLength();

        if (arrayLength == 1)
        {
            return single.get();
        }
        else if (arrayLength > 1)
        {
            return array.get();
        }

        return "";
    }

    /**
     * The offset for this token in the message.
     *
     * @return the offset of this Token. A value of 0 means the node has no relevant offset. A value of
     * {@link Token#UNKNOWN_OFFSET} means this nodes true offset is dependent on variable length
     * fields ahead of it in the encoding.
     */
    public int offset()
    {
        return offset;
    }

    /**
     * Return the {@link Encoding} of the {@link Token}.
     *
     * @return encoding of the {@link Token}
     */
    public Encoding encoding()
    {
        return encoding;
    }

    public boolean isConstantEncoding()
    {
        return encoding.presence() == Encoding.Presence.CONSTANT;
    }

    public boolean isOptionalEncoding()
    {
        return encoding.presence() == Encoding.Presence.OPTIONAL;
    }

    public String toString()
    {
        return
            "Token{" +
            "signal=" + signal +
            ", name='" + name + '\'' +
            ", id=" + id +
            ", version=" + version +
            ", encodedLength=" + encodedLength +
            ", offset=" + offset +
            ", encoding=" + encoding +
            '}';
    }

    public static class Builder
    {
        private Signal signal;
        private String name;
        private int id = INVALID_ID;
        private int version = 0;
        private int size = 0;
        private int offset = 0;
        private Encoding encoding = new Encoding();

        public Builder signal(final Signal signal)
        {
            this.signal = signal;
            return this;
        }

        public Builder name(final String name)
        {
            this.name = name;
            return this;
        }

        public Builder id(final int id)
        {
            this.id = id;
            return this;
        }

        public Builder version(final int version)
        {
            this.version = version;
            return this;
        }

        public Builder size(final int size)
        {
            this.size = size;
            return this;
        }

        public Builder offset(final int offset)
        {
            this.offset = offset;
            return this;
        }

        public Builder encoding(final Encoding encoding)
        {
            this.encoding = encoding;
            return this;
        }

        public Token build()
        {
            return new Token(signal, name, id, version, size, offset, encoding);
        }
    }
}
