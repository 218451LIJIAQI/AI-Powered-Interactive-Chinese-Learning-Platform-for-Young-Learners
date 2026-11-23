package com.example.aichinese.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Timestamps are kept as server-local {@link LocalDateTime} (entities, {@code LocalDateTime.now()}), and a browser
 * reads an ISO date-time without an offset as its own local time. So every {@code LocalDateTime} in a response is
 * written with the server's offset ({@code 2026-11-14T13:00:00+08:00}): the viewer sees the right moment whatever
 * zone the server or the viewer is in. A request may send such a timestamp back (a practice run's start): with an
 * offset it is read as that moment in server-local time, without one as it is.
 */
@Configuration(proxyBeanMethods = false)
public class JsonTimeConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer serverLocalTimestampsWithOffset() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new ServerLocalDateTimeSerializer())
                .deserializerByType(LocalDateTime.class, new ServerLocalDateTimeDeserializer());
    }

    private static final class ServerLocalDateTimeSerializer extends StdSerializer<LocalDateTime> {

        ServerLocalDateTimeSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(
                    value.atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    private static final class ServerLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

        ServerLocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            if (text == null) {
                return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
            }
            String value = text.strip();
            if (value.isEmpty()) {
                return null;
            }
            try {
                TemporalAccessor parsed =
                        DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from, LocalDateTime::from);
                return parsed instanceof OffsetDateTime withOffset
                        ? withOffset.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        : (LocalDateTime) parsed;
            } catch (DateTimeParseException exception) {
                return (LocalDateTime) context.handleWeirdStringValue(
                        LocalDateTime.class, value, "Expected an ISO date-time, with or without an offset");
            }
        }
    }
}
