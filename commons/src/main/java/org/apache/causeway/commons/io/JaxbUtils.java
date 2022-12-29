/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.causeway.commons.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

import org.springframework.lang.Nullable;

import org.apache.causeway.commons.functional.Try;
import org.apache.causeway.commons.internal.base._Casts;
import org.apache.causeway.commons.internal.base._NullSafe;
import org.apache.causeway.commons.internal.codec._DocumentFactories;
import org.apache.causeway.commons.internal.collections._Arrays;
import org.apache.causeway.commons.internal.collections._Lists;
import org.apache.causeway.commons.internal.collections._Maps;
import org.apache.causeway.commons.internal.exceptions._Exceptions;
import org.apache.causeway.commons.internal.functions._Functions;
import org.apache.causeway.commons.internal.reflection._ClassCache;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.val;
import lombok.experimental.UtilityClass;

/**
 * Utilities to convert from and to JAXB-XML format.
 *
 * @since 2.0 {@index}
 */
@UtilityClass
public class JaxbUtils {

    /** uses given factory as default */
    public void setDefaultJAXBContextFactory(final Class<?> jaxbContextFactoryClass, final boolean force) {
        if(force
                || System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY)==null) {
            if(jaxbContextFactoryClass!=null) {
                System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY, jaxbContextFactoryClass.getName());
            } else {
                System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
            }
        }
    }

    /** uses MOXy */
    public void useMoxy() {
        setDefaultJAXBContextFactory(org.eclipse.persistence.jaxb.JAXBContextFactory.class, true);
    }

    /** clears the system property override */
    public static void usePlatformDefault() {
        setDefaultJAXBContextFactory(null, true);
    }

    @Data @Builder
    public static class JaxbOptions {
        private final @Builder.Default boolean useContextCache = true;
        private final @Builder.Default boolean allowMissingRootElement = false;
        private final @Builder.Default boolean formattedOutput = false;
        private final @Singular Map<String, Object> properties;
        private final @Builder.Default @NonNull Consumer<Marshaller> marshallerConfigurer = _Functions.noopConsumer();
        private final @Builder.Default @NonNull Consumer<Unmarshaller> unmarshallerConfigurer = _Functions.noopConsumer();
        private final @Nullable JAXBContext jaxbContextOverride;
        public static JaxbOptions defaults() {
            return JaxbOptions.builder().build();
        }

        // -- HELPER

        private boolean shouldMissingXmlRootElementBeHandledOn(final Class<?> mappedType) {
            return isAllowMissingRootElement()
                    // looking for presence of XmlRootElement annotation
                    && !_ClassCache.getInstance().hasJaxbRootElementSemantics(mappedType);
        }
        @SneakyThrows
        private JAXBContext jaxbContext(final Class<?> mappedType) {
            return jaxbContextOverride!=null
                    ? jaxbContextOverride
                    : jaxbContextFor(mappedType, useContextCache);
        }
        @SneakyThrows
        private Marshaller marshaller(final JAXBContext jaxbContext, final Class<?> mappedType) {
            val marshaller = jaxbContext.createMarshaller();
            if(properties!=null) {
                for(val entry : properties.entrySet()) {
                    marshaller.setProperty(entry.getKey(), entry.getValue());
                }
            }
            if(isFormattedOutput()) {
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            }
            return marshaller;
        }
        @SneakyThrows
        private Unmarshaller unmarshaller(final JAXBContext jaxbContext, final Class<?> mappedType) {
            val unmarshaller = jaxbContext.createUnmarshaller();
            if(properties!=null) {
                for(val entry : properties.entrySet()) {
                    unmarshaller.setProperty(entry.getKey(), entry.getValue());
                }
            }
            return unmarshaller;
        }
        @SneakyThrows
        private <T> T unmarshal(final Unmarshaller unmarshaller, final Class<T> mappedType, final InputStream is) {
            unmarshallerConfigurer.accept(unmarshaller);
            return shouldMissingXmlRootElementBeHandledOn(mappedType)
                    ? unmarshalTypesafe(unmarshaller, mappedType, is)
                    : _Casts.castTo(mappedType, unmarshaller.unmarshal(is))
                        .orElseGet(()->unmarshalTypesafe(unmarshaller, mappedType, is));
        }
        @SneakyThrows
        private <T> T unmarshalTypesafe(final Unmarshaller unmarshaller, final Class<T> mappedType, final InputStream is) {
            val xsr = _DocumentFactories.xmlInputFactory().createXMLStreamReader(is);
            final JAXBElement<T> userElement = unmarshaller.unmarshal(xsr, mappedType);
            return userElement.getValue();
        }
        @SneakyThrows
        private <T> void marshal(final Marshaller marshaller, final T pojo, final OutputStream os) {
            marshallerConfigurer.accept(marshaller);
            @SuppressWarnings("unchecked")
            val mappedType = (Class<T>)pojo.getClass();
            if(shouldMissingXmlRootElementBeHandledOn(mappedType)) {
                val qName = new QName("", mappedType.getSimpleName());
                val jaxbElement = new JAXBElement<T>(qName, mappedType, null, pojo);
                marshaller.marshal(jaxbElement, os);
            } else {
                marshaller.marshal(pojo, os);
            }
        }
        private <T> T unmarshal(final JAXBContext jaxbContext, final Class<T> mappedType, final InputStream is) {
            return unmarshal(unmarshaller(jaxbContext, mappedType), mappedType, is);
        }
        private <T> void marshal(final JAXBContext jaxbContext, final T pojo, final OutputStream os) {
            @SuppressWarnings("unchecked")
            val mappedType = (Class<T>)pojo.getClass();
            marshal(marshaller(jaxbContext, mappedType), pojo, os);
        }
        private <T> T unmarshal(final Class<T> mappedType, final InputStream is) {
            return unmarshal(jaxbContext(mappedType), mappedType, is);
        }
        private <T> void marshal(final T pojo, final OutputStream os) {
            @SuppressWarnings("unchecked")
            val mappedType = (Class<T>)pojo.getClass();
            marshal(jaxbContext(mappedType), pojo, os);
        }
    }

    @FunctionalInterface
    public interface JaxbCustomizer extends UnaryOperator<JaxbOptions.JaxbOptionsBuilder> {}

    // -- MAPPER

    public <T> DtoMapper<T> mapperFor(final @NonNull Class<T> mappedType, final JaxbUtils.JaxbCustomizer ... customizers) {

        val opts = createOptions(customizers);
        val jaxbContext = opts.jaxbContext(mappedType); // cached with this instance of DtoMapper

        return new DtoMapper<T>() {

            @Override
            public T read(final DataSource source) {
                return source.readAll((final InputStream is)->{
                    return Try.call(()->opts.unmarshal(jaxbContext, mappedType, is));
                })
                .ifFailureFail()
                .getValue().orElseThrow();
            }

            @Override
            public void write(final T dto, final DataSink sink) {
                if(dto==null) return;
                sink.writeAll(os->Try.run(()->opts.marshal(jaxbContext, dto, os)));
            }

        };
    }


    // -- READING

    /**
     * Tries to deserialize JAXB-XML content from given UTF8 encoded {@link String}
     * into an instance of given {@code mappedType}.
     */
    public <T> Try<T> tryRead(
            final @NonNull Class<T> mappedType,
            final @Nullable String stringUtf8,
            final JaxbUtils.JaxbCustomizer ... customizers) {
        return tryRead(mappedType, DataSource.ofStringUtf8(stringUtf8), customizers);
    }

    /**
     * Tries to deserialize JAXB-XML content from given {@link DataSource} into an instance of
     * given {@code mappedType}.
     */
    public <T> Try<T> tryRead(
            final @NonNull Class<T> mappedType,
            final @NonNull DataSource source,
            final JaxbUtils.JaxbCustomizer ... customizers) {
        return source.readAll((final InputStream is)->{
            val opts = createOptions(customizers);
            return Try.call(()->opts.unmarshal(mappedType, is))
                    .mapFailure(cause->verboseException("unmarshalling XML", mappedType, cause));
        });
    }

    // -- WRITING

    /**
     * Writes given {@code pojo} to given {@link DataSink}.
     */
    public <T> void write(
            final @Nullable T pojo,
            final @NonNull DataSink sink,
            final JaxbUtils.JaxbCustomizer ... customizers) {
        if(pojo==null) return;
        val opts = createOptions(customizers);
        try {
            sink.writeAll(os->Try.run(()->opts.marshal(pojo, os)));
        } catch (Exception cause) {
            throw verboseException("marshalling domain object to XML", pojo.getClass(), cause);
        }
    }

    /**
     * Converts given {@code pojo} to an UTF8 encoded {@link String}.
     * @return <code>null</code> if pojo is <code>null</code>
     */
    @Nullable
    public <T> String toStringUtf8(
            final @Nullable T pojo,
            final JaxbUtils.JaxbCustomizer ... customizers) {
        if(pojo==null) return null;
        val sh = _Lists.<String>newArrayList(1);
        write(pojo, DataSink.ofStringUtf8Consumer(sh::add), customizers);
        return sh.stream().findFirst().orElse(null);
    }

    // -- CUSTOMIZERS

    // -- MAPPER FACTORY

    private JaxbOptions createOptions(
            final JaxbUtils.JaxbCustomizer ... customizers) {
        var opts = JaxbOptions.builder();
        for(JaxbUtils.JaxbCustomizer customizer : customizers) {
            opts = Optional.ofNullable(customizer.apply(opts))
                    .orElse(opts);
        }
        return opts.build();
    }

    // -- JAXB CONTEXT FACTORIES AND CACHING

    /** not cached */
    public static JAXBContext jaxbContextFor(final @NonNull Class<?> primaryClass, final Class<?> ... additionalClassesToBeBound) {
        return contextOf(_Arrays.combine(primaryClass, additionalClassesToBeBound));
    }

    private static Map<Class<?>, JAXBContext> jaxbContextByClass = _Maps.newConcurrentHashMap();

    public static JAXBContext jaxbContextFor(final Class<?> dtoClass, final boolean useCache)  {
        return useCache
                ? jaxbContextByClass.computeIfAbsent(dtoClass, JaxbUtils::contextOf)
                : contextOf(dtoClass);
    }

    @SneakyThrows
    private static <T> JAXBContext contextOf(final Class<?> ... classesToBeBound) {
        try {
            return JAXBContext.newInstance(classesToBeBound);
        } catch (Exception e) {
            val msg = String.format("obtaining JAXBContext for classes (to be bound) {%s}", _NullSafe.stream(classesToBeBound)
                    .map(Class::getName)
                    .collect(Collectors.joining(", ")));
            throw verboseException(msg, classesToBeBound[0], e); // assuming we have at least one argument
        }
    }

    // -- ENHANCE EXCEPTION MESSAGE IF POSSIBLE

    private static RuntimeException verboseException(final String doingWhat, @Nullable final Class<?> dtoClass, final Throwable cause) {

        val dtoClassName = Optional.ofNullable(dtoClass).map(Class::getName).orElse("unknown");

        if(isIllegalAnnotationsException(cause)) {
            // report a better error if possible
            // this is done reflectively because on JDK 8 this exception type is only provided by Oracle JDK
            try {

                val errors = _Casts.<List<? extends Exception>>uncheckedCast(
                        cause.getClass().getMethod("getErrors").invoke(cause));

                if(_NullSafe.size(errors)>0) {

                    return _Exceptions.unrecoverable(cause,
                            "Error %s, "
                            + "due to illegal annotations on object class '%s'; "
                            + "%d error(s) reported: %s",
                            doingWhat,
                            dtoClassName,
                            errors.size(),
                            errors.stream()
                                .map(Exception::getMessage)
                                .collect(Collectors.joining("; ")));
                }

            } catch (Exception ex) {
                // just fall through if we hit any issues
            }
        }

        return _Exceptions.unrecoverable(cause,
                "Error %s; object class is '%s'", doingWhat, dtoClassName);
    }

    private static boolean isIllegalAnnotationsException(final Throwable cause) {
        /*sonar-ignore-on*/
        return "com.sun.xml.bind.v2.runtime.IllegalAnnotationsException".equals(cause.getClass().getName());
        /*sonar-ignore-off*/
    }




}
