FROM mcr.microsoft.com/devcontainers/java:0-17

# Fix Yarn repo GPG key (old key expired/rotated)
RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg \
      | gpg --dearmor \
      | tee /usr/share/keyrings/yarn-archive-keyring.gpg >/dev/null \
    && if [ -f /etc/apt/sources.list.d/yarn.list ]; then \
         sed -i 's|deb https://dl.yarnpkg.com|deb [signed-by=/usr/share/keyrings/yarn-archive-keyring.gpg] https://dl.yarnpkg.com|g' \
           /etc/apt/sources.list.d/yarn.list; \
       fi
