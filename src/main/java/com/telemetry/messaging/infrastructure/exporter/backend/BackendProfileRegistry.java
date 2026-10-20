package com.telemetry.messaging.infrastructure.exporter.backend;

import com.telemetry.messaging.domain.backend.BackendProfile;
import com.telemetry.messaging.domain.backend.TelemetryBackend;
import com.telemetry.messaging.domain.exception.InvalidTelemetryConfigurationException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the available profiles. Profiles registered by the user replace the defaults for the same backend.
 */
public class BackendProfileRegistry {

    private final Map<TelemetryBackend, BackendProfile> profiles = new EnumMap<>(TelemetryBackend.class);

    public BackendProfileRegistry(List<BackendProfile> customProfiles) {
        register(new NewRelicBackendProfile());
        register(new DynatraceBackendProfile());
        register(new ElasticBackendProfile());
        register(new OtlpBackendProfile());
        customProfiles.forEach(this::register);
    }

    private void register(BackendProfile profile) {
        profiles.put(profile.backend(), profile);
    }

    public BackendProfile profileFor(TelemetryBackend backend) {
        BackendProfile profile = profiles.get(backend);
        if (profile == null) {
            throw new InvalidTelemetryConfigurationException("Não existe perfil de exportação OTLP para " + backend);
        }
        return profile;
    }
}
