package com.fcareer.catalog.squad;

import java.time.Instant;

public record SquadVersion(long id, short gameVersionId, int versionNo, String label, String gameBuild, Instant exportedAt, int playerCount,
                           Instant createdAt) {
}
