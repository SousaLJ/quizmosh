package io.quizmosh.protocol;

import java.util.Objects;

public record ProtocolEnvelope(String protocolVersion, String type, String correlationId, Object payload) {
    public ProtocolEnvelope {
        protocolVersion = protocolVersion == null ? ProtocolVersion.CURRENT : protocolVersion;
        Objects.requireNonNull(type);
        correlationId = correlationId == null ? "" : correlationId;
    }
    public static ProtocolEnvelope of(String type, String correlationId, Object payload) {
        return new ProtocolEnvelope(ProtocolVersion.CURRENT, type, correlationId, payload);
    }
}
