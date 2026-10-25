# The F-Droid build server image plus the Debian packages app.mobilecad.yml installs (for local test builds)
FROM registry.gitlab.com/fdroid/fdroidserver:buildserver
RUN apt-get update -qq && apt-get install -y -qq build-essential cmake ninja-build pkg-config perl patch xz-utils bzip2 curl git libpcre2-dev bison \
    swig file unzip python3-venv libssl-dev zlib1g-dev libffi-dev libsqlite3-dev libbz2-dev liblzma-dev libzstd-dev libreadline-dev uuid-dev \
    && apt-get install -y -qq -t trixie-backports flang-21 && rm -rf /var/lib/apt/lists/*
