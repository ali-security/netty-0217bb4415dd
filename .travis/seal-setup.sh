# Sealed build environment setup, sourced by .travis.yml (before_install) and by
# .github/workflows/seal-build.yml. Do not use `set -e`: this file is sourced.
#
# 1. Zulu JDK 7 ($SEAL_JDK): Netty 3.7.0.Final targets Java 5 bytecode and its
#    enforcer rule requires JDK >= 1.7; the published jar was built on 1.7.0_25.
#    The CA bundle of the runner's JDK 8 replaces Zulu 7's, which is too old for
#    Maven Central's certificate chain.
# 2. Maven $SEAL_MAVEN (runs on Java 7).
# 3. MAVEN_OPTS: TLSv1.2 for Maven Central, and java.security overrides from
#    .travis/seal-java.security (surefire runs in-process, forkMode=never): the
#    securechat example keystore used by the *SocketSslEchoTest tests holds a
#    512-bit RSA certificate that Java 7u352's default disabledAlgorithms reject.
SEAL_JDK=${SEAL_JDK:-zulu7.56.0.11-ca-jdk7.0.352-linux_x64}
SEAL_MAVEN=${SEAL_MAVEN:-3.8.8}
SEAL_ROOT=${SEAL_ROOT:-$(pwd)}
SEAL_DONOR_JAVA=${SEAL_DONOR_JAVA:-$JAVA_HOME}

mkdir -p /tmp/seal-jdk || return 1
curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-jdk.tar.gz "https://cdn.azul.com/zulu/bin/$SEAL_JDK.tar.gz" || return 1
tar -xzf /tmp/seal-jdk.tar.gz -C /tmp/seal-jdk --strip-components=1 || return 1
SEAL_CACERTS="$SEAL_DONOR_JAVA/jre/lib/security/cacerts"
[ -f "$SEAL_CACERTS" ] || SEAL_CACERTS="$SEAL_DONOR_JAVA/lib/security/cacerts"
cp "$SEAL_CACERTS" /tmp/seal-jdk/jre/lib/security/cacerts || return 1
export JAVA_HOME=/tmp/seal-jdk

curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-maven.tar.gz \
  "https://archive.apache.org/dist/maven/maven-3/$SEAL_MAVEN/binaries/apache-maven-$SEAL_MAVEN-bin.tar.gz" || return 1
tar -xzf /tmp/seal-maven.tar.gz -C /tmp || return 1
export M2_HOME="/tmp/apache-maven-$SEAL_MAVEN"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
export MAVEN_OPTS="-Dhttps.protocols=TLSv1.2 -Djava.security.properties=$SEAL_ROOT/.travis/seal-java.security"

java -version
mvn -version
