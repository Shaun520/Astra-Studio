package com.example.astrastudioopenai.service.conversation;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Component
public class KryoSerializer {

    private static final Logger log = LoggerFactory.getLogger(KryoSerializer.class);

    private static final ThreadLocal<Kryo> kryoPool = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setDefaultSerializer(com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer.class);
        kryo.register(ConversationContext.class);
        kryo.register(com.example.astrastudioopenai.dto.response.MessageEntry.class);
        return kryo;
    });

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] toBytes(ConversationContext ctx) {
        if (ctx == null) return new byte[0];
        Kryo kryo = kryoPool.get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        Output output = new Output(baos, 4096);
        try {
            kryo.writeClassAndObject(output, ctx);
            output.flush();
            return baos.toByteArray();
        } finally {
            output.close();
        }
    }

    @SuppressWarnings("unchecked")
    public ConversationContext fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        Kryo kryo = kryoPool.get();
        Input input = new Input(new ByteArrayInputStream(bytes));
        try {
            Object obj = kryo.readClassAndObject(input);
            if (obj instanceof ConversationContext) {
                return (ConversationContext) obj;
            }
            log.warn("Kryo deserialized unexpected type: {}", obj.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("Kryo deserialization failed, attempting JSON fallback", e);
            try {
                return objectMapper.readValue(bytes, ConversationContext.class);
            } catch (Exception ex) {
                log.error("Both Kryo and JSON fallback failed", ex);
                return null;
            }
        } finally {
            input.close();
        }
    }

    public String computeChecksum(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean validateChecksum(byte[] bytes, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isEmpty()) return true;
        String actual = computeChecksum(bytes);
        boolean valid = actual.equals(expectedChecksum);
        if (!valid) {
            log.error("Checksum mismatch: expected={}, actual={}", expectedChecksum, actual);
        }
        return valid;
    }
}
