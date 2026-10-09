#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ARG MAVEN_BUILDER_IMAGE=public.ecr.aws/docker/library/maven@sha256:e4a7ace3dc0d645ed97f8d9ad0b0d3f0b14fa8d150138f27f116d7105a639b82
FROM --platform=$BUILDPLATFORM ${MAVEN_BUILDER_IMAGE} AS build

ENV NODE_OPTIONS=--dns-result-order=ipv4first

ARG MAVEN_ARGS

WORKDIR /pkg

RUN set -x \
    && apt-get -q update \
    && apt-get install --no-install-recommends curl git python3 -yq \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*


COPY hugegraph-client/assembly/travis/install-candidate-sdk.sh \
     hugegraph-client/assembly/travis/checkout-server.sh /pkg/hugegraph-client/assembly/travis/
COPY .github/scripts/verify_candidate_image_sdk.py /pkg/.github/scripts/

# Direct builds bootstrap the same locked SDK; CI can supply a verified builder.
RUN mkdir -p /opt/candidate-m2 \
    && if [ ! -f /opt/candidate-m2/candidate-sdk-manifest.json ]; then \
         SERVER_REPOSITORY=apache/hugegraph \
         bash /pkg/hugegraph-client/assembly/travis/install-candidate-sdk.sh \
           68855199031d5801edb4fe41b2bacbacffe8fe68 /opt/candidate-source /opt/candidate-m2; \
       fi \
    && python3 /pkg/.github/scripts/verify_candidate_image_sdk.py /opt/candidate-m2

COPY . /pkg

RUN set -x \
    && mvn install $MAVEN_ARGS -Dmaven.repo.local=/opt/candidate-m2 \
       -Dhugegraph.version=1.8.0 -Dsdk.validation.mode=candidate \
       -pl hugegraph-client,hugegraph-loader -am -Dmaven.javadoc.skip=true -DskipTests -ntp \
    && cd /pkg/hugegraph-hubble/ \
    && mvn package $MAVEN_ARGS -Dmaven.repo.local=/opt/candidate-m2 \
       -Dhugegraph.version=1.8.0 -Dsdk.validation.mode=candidate \
       -e -B -ntp -DskipTests -Dmaven.javadoc.skip=true \
    && python3 /pkg/.github/scripts/verify_candidate_image_sdk.py /opt/candidate-m2 \
    && set -- /pkg/hugegraph-hubble/target/apache-hugegraph-hubble-*.tar.gz \
    && test "$#" -eq 1 \
    && mkdir /hubble-release \
    && tar -xzf "$1" --strip-components=1 -C /hubble-release

FROM public.ecr.aws/docker/library/eclipse-temurin@sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7

RUN apt-get -q update \
    && apt-get install --no-install-recommends curl -yq \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /hubble-release/ /hubble
RUN sed -i \
    -e 's/^server\.host=.*/server.host=0.0.0.0/' \
    -e 's/^dashboard\.address=.*/dashboard.address=/' \
    /hubble/conf/hugegraph-hubble.properties \
    && grep -Fqx 'server.host=0.0.0.0' \
        /hubble/conf/hugegraph-hubble.properties \
    && grep -Fqx 'dashboard.address=' \
        /hubble/conf/hugegraph-hubble.properties
WORKDIR /hubble/

# SECURITY: This is a plain HTTP port. Do not publish it to an untrusted network;
# production/public deployments must put Hubble behind HTTPS and block bypasses.
EXPOSE 8088
ENTRYPOINT ["./bin/start-hubble.sh", "-f"]
