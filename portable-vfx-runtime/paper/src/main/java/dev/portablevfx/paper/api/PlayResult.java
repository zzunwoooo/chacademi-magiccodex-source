package dev.portablevfx.paper.api;

import java.util.UUID;

/** A handle is active only if at least one recipient was sent the effect. */
public record PlayResult(UUID handle, int recipients, int skippedRateLimited, double effectiveRadius) {}
