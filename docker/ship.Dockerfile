# The vendored ship tool's tests: node (as tools/ship/run-node uses), git and bash, nothing else.
FROM node:24.22.0-slim@sha256:a5f4366e834695d5f363b920c104953a535d3a9864d1bdf74566a0ebe01daee5
RUN apt-get update \
 && apt-get install --yes --no-install-recommends git=1:2.39.5-0+deb12u3 \
 && rm -rf /var/lib/apt/lists/*
USER node
