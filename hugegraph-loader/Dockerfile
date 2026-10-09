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

WORKDIR /pkg

ARG MAVEN_ARGS

RUN apt-get -q update \
    && apt-get install --no-install-recommends git python3 -yq \
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
    && mvn clean package $MAVEN_ARGS -Dmaven.repo.local=/opt/candidate-m2 \
       -Dhugegraph.version=1.8.0 -Dsdk.validation.mode=candidate \
       -pl hugegraph-client,hugegraph-loader -am \
       -Dmaven.javadoc.skip=true -DskipTests -ntp \
    && python3 /pkg/.github/scripts/verify_candidate_image_sdk.py /opt/candidate-m2

FROM public.ecr.aws/docker/library/eclipse-temurin@sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7

RUN set -x \
    && apt-get -q update \
    && apt-get -q install -y --no-install-recommends --no-install-suggests \
       dumb-init \
       procps \
       curl \
       lsof \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /pkg/hugegraph-loader/apache-hugegraph-loader-*/ /loader
WORKDIR /loader/

VOLUME /loader

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["tail","-f","/dev/null"]
