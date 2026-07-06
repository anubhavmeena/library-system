#!/bin/sh
set -e

# /app/uploads is a named volume shared with admin-service. Docker only applies
# the image's baked-in ownership to a volume the FIRST time it's mounted empty —
# an already-populated volume keeps whatever ownership it had before, which can
# end up owned by a different UID than this image's "spring" user resolves to.
# Fixing ownership here, at every startup (while still root, before dropping
# privileges), makes this self-healing regardless of the volume's prior state.
if [ -d /app/uploads ]; then
    chown -R spring:spring /app/uploads 2>/dev/null || true
fi

exec su-exec spring java -jar -Dspring.profiles.active=docker /app/app.jar
