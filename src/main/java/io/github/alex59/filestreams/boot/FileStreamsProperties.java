package io.github.alex59.filestreams.boot;

import io.github.alex59.filestreams.StreamProfile;
import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Typed settings; both storage integration and HTTP exposure require explicit activation. */
@ConfigurationProperties("file-streams")
public record FileStreamsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue Limits limits,
        @DefaultValue S3 s3,
        @DefaultValue Http http) {

    public record Limits(
            DataSize maxObjectSize,
            @DefaultValue("1MB") DataSize maxBufferSize,
            @DefaultValue("2") int maxConcurrentTransfers,
            @DefaultValue("ARCHIVES") StreamProfile profile) {
        @ConstructorBinding
        public Limits {
            Objects.requireNonNull(profile, "profile");
            if (maxObjectSize == null) maxObjectSize = DataSize.ofBytes(profile.maxBytes());
            Objects.requireNonNull(maxObjectSize, "maxObjectSize");
            Objects.requireNonNull(maxBufferSize, "maxBufferSize");
            if (maxObjectSize.toBytes() < 0) throw new IllegalArgumentException("max-object-size must be nonnegative");
            if (maxBufferSize.toBytes() < 1 || maxBufferSize.toBytes() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("max-buffer-size must be between 1B and 2147483647B");
            }
            if (maxConcurrentTransfers < 1) throw new IllegalArgumentException("max-concurrent-transfers must be positive");
        }

        /** Retains the original explicit-limits constructor. */
        public Limits(DataSize maxObjectSize, DataSize maxBufferSize, int maxConcurrentTransfers) {
            this(Objects.requireNonNull(maxObjectSize, "maxObjectSize"), maxBufferSize, maxConcurrentTransfers, StreamProfile.ARCHIVES);
        }
    }

    public record S3(String bucket, String region, URI endpoint) {
        public S3 {
            if (endpoint != null && (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null || endpoint.getFragment() != null)) {
                throw new IllegalArgumentException("s3.endpoint must be an HTTP(S) URL without credentials/query/fragment");
            }
        }
    }

    public record Http(@DefaultValue("false") boolean enabled) {}
}
