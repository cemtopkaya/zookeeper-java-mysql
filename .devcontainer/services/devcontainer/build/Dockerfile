FROM debian:bullseye

# Temel paketlerin kurulumu, Java 17, Maven, Docker CLI kurulumu
RUN apt-get update && apt-get install -y \
    ca-certificates \
    bash-completion curl \
    gnupg \
    lsb-release \
    git \
    unzip \
    vim \
    iproute2 \
    iputils-ping \
    net-tools \
    dnsutils

RUN apt-get install -y openjdk-17-jdk maven

# Docker CLI kurulumu
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update

# RUN apt-get install -y docker.io
# RUN apt-get install -y docker-compose-plugin

# Add Docker's official GPG key:
RUN install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc && \
    chmod a+r /etc/apt/keyrings/docker.asc && \
    # Add the repository to Apt sources:
    echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli docker-buildx-plugin docker-compose-plugin


RUN curl -fsSL https://raw.githubusercontent.com/docker/cli/master/contrib/completion/bash/docker -o /etc/bash_completion.d/docker.sh \
    && echo "source /etc/bash_completion.d/docker.sh" >> /etc/bash.bashrc


RUN apt-get clean && rm -rf /var/lib/apt/lists/*

# JAVA_HOME ortam değişkeni
# ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# ENV PATH=$JAVA_HOME/bin:$PATH

RUN echo "export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))" > /etc/profile.d/javahome.sh

WORKDIR /app

CMD ["sleep", "infinity"]
